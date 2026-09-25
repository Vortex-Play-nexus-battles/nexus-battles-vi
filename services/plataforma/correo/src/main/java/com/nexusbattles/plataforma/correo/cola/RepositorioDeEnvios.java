package com.nexusbattles.plataforma.correo.cola;

import com.nexusbattles.plataforma.correo.envio.Enmascarar;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * La tabla {@code correo.envios}: la cola persistente de B1.
 *
 * <p><b>JDBC a secas, no JPA.</b> Una tabla y media docena de sentencias no
 * justifican Hibernate en un host donde doce JVM comparten 2 GiB
 * (infrastructure/despliegue/CAPACIDAD.md); el mismo criterio que
 * metricas-plataforma. Todas las fechas viajan como {@code timestamptz} en UTC
 * y salen del reloj del servicio, no del de la base: asi las pruebas pueden
 * mover el tiempo y las dos cuentas nunca se contradicen.
 *
 * <p><b>Como no se envia dos veces la misma fila.</b> Reclamar es un solo
 * {@code UPDATE} sobre las filas elegidas con {@code FOR UPDATE SKIP LOCKED}:
 * dos trabajadores -dos hilos o dos instancias- que reclaman a la vez se
 * saltan las filas que el otro ya tiene bloqueadas, y al confirmar quedan en
 * {@code ENVIANDO}, que nadie mas elige. Cada reclamo suma un intento, y las
 * escrituras posteriores exigen ese mismo numero de intentos: si otro
 * trabajador volvio a reclamar la fila entretanto, la escritura vieja no la
 * pisa.
 */
@Repository
public class RepositorioDeEnvios {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> MAPA = new TypeReference<>() {};

    private static final String COLUMNAS_EN_COLA =
            "id, plantilla, destinatario, asunto, datos, intentos, trace_id, creado_en";

    private final JdbcClient jdbc;

    public RepositorioDeEnvios(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Guarda un envio nuevo.
     *
     * @return false si ya habia uno con la misma clave de idempotencia (y
     *         entonces no se guarda nada)
     */
    public boolean insertar(NuevoEnvio envio) {
        int filas = jdbc.sql("""
                        INSERT INTO correo.envios (
                            id, plantilla, destinatario, asunto, datos, estado, intentos, proximo_intento,
                            ultimo_error, idempotency_key, trace_id, creado_en, actualizado_en)
                        VALUES (
                            :id, :plantilla, :destinatario, :asunto, CAST(:datos AS jsonb), :estado, 0, :ahora,
                            :motivo, :clave, :traza, :ahora, :ahora)
                        ON CONFLICT (idempotency_key) DO NOTHING
                        """)
                .param("id", envio.id())
                .param("plantilla", envio.plantilla())
                .param("destinatario", envio.destinatario())
                .param("asunto", envio.asunto())
                .param("datos", aJson(envio.datos()))
                .param("estado", envio.estado().name())
                .param("ahora", utc(envio.creadoEn()))
                .param("motivo", envio.motivo(), Types.VARCHAR)
                .param("clave", envio.claveDeIdempotencia(), Types.VARCHAR)
                .param("traza", envio.trazaId(), Types.VARCHAR)
                .update();
        return filas == 1;
    }

    /**
     * Reclama hasta {@code lote} envios vencidos: los pasa a ENVIANDO y les
     * suma un intento, en una sola sentencia.
     *
     * <p>{@code MATERIALIZED} obliga a elegir y bloquear las filas UNA vez;
     * sin el, el planificador podria reevaluar la subconsulta y reclamar mas
     * filas de las pedidas.
     */
    public List<EnvioEnCola> reclamar(int lote, Instant ahora) {
        return jdbc.sql("""
                        WITH lote AS MATERIALIZED (
                            SELECT id FROM correo.envios
                             WHERE estado IN ('PENDIENTE', 'ERROR_REINTENTABLE')
                               AND proximo_intento <= :ahora
                             ORDER BY proximo_intento, creado_en
                             LIMIT :lote
                             FOR UPDATE SKIP LOCKED)
                        UPDATE correo.envios e
                           SET estado = 'ENVIANDO', intentos = e.intentos + 1, actualizado_en = :ahora
                          FROM lote
                         WHERE e.id = lote.id
                        RETURNING e.id, e.plantilla, e.destinatario, e.asunto, e.datos, e.intentos,
                                  e.trace_id, e.creado_en
                        """)
                .param("ahora", utc(ahora))
                .param("lote", lote)
                .query(RepositorioDeEnvios::enCola)
                .list()
                .stream()
                .sorted((a, b) -> a.creadoEn().compareTo(b.creadoEn()))
                .toList();
    }

    /**
     * Confirma, justo antes de enviar, que la fila sigue siendo de quien la
     * reclamo. Si otro trabajador la recupero como atascada o la volvio a
     * reclamar, devuelve false y no hay que enviarla.
     */
    public boolean renovarReclamo(UUID id, int intentos, Instant ahora) {
        return jdbc.sql("""
                        UPDATE correo.envios SET actualizado_en = :ahora
                         WHERE id = :id AND estado = 'ENVIANDO' AND intentos = :intentos
                        """)
                .param("ahora", utc(ahora))
                .param("id", id)
                .param("intentos", intentos)
                .update() == 1;
    }

    /**
     * Escribe el resultado de un intento.
     *
     * <p>Vale mientras nadie haya vuelto a reclamar la fila (mismo numero de
     * intentos), aunque la recuperacion de atascados la haya movido
     * entretanto: el resultado real de un envio lento manda sobre la
     * suposicion de que se habia caido.
     *
     * @return false si la fila ya no era de este intento
     */
    public boolean registrarResultado(UUID id, int intentos, CambioDeEstado cambio, Instant ahora) {
        return jdbc.sql("""
                        UPDATE correo.envios
                           SET estado = :estado,
                               proximo_intento = :proximo,
                               ultimo_error = :error,
                               destino = :destino,
                               identificador = :identificador,
                               datos = COALESCE(CAST(:datos AS jsonb), datos),
                               enviado_en = :enviadoEn,
                               actualizado_en = :ahora
                         WHERE id = :id AND intentos = :intentos
                           AND estado IN ('ENVIANDO', 'ERROR_REINTENTABLE', 'FALLIDO')
                        """)
                .param("estado", cambio.estado().name())
                .param("proximo", utc(cambio.proximoIntento() == null ? ahora : cambio.proximoIntento()))
                .param("error", cambio.error(), Types.VARCHAR)
                .param("destino", cambio.destino() == null ? null : cambio.destino().name(), Types.VARCHAR)
                .param("identificador", cambio.identificador(), Types.VARCHAR)
                .param("datos", cambio.datos() == null ? null : aJson(cambio.datos()), Types.VARCHAR)
                .param("enviadoEn", utc(cambio.enviadoEn()), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("ahora", utc(ahora))
                .param("id", id)
                .param("intentos", intentos)
                .update() == 1;
    }

    /**
     * Envios que llevan en ENVIANDO desde antes de {@code limite}: el servicio
     * se cayo a mitad de envio. Bloquea las filas devueltas, asi que hay que
     * llamarlo dentro de una transaccion y escribir su resultado en ella.
     */
    public List<EnvioEnCola> tomarAtascados(Instant limite, int lote) {
        return jdbc.sql("SELECT " + COLUMNAS_EN_COLA + """
                         FROM correo.envios
                        WHERE estado = 'ENVIANDO' AND actualizado_en < :limite
                        ORDER BY actualizado_en
                        LIMIT :lote
                        FOR UPDATE SKIP LOCKED
                        """)
                .param("limite", utc(limite))
                .param("lote", lote)
                .query(RepositorioDeEnvios::enCola)
                .list();
    }

    /** Cuantos envios hay en cada estado (los que no tienen ninguno no aparecen). */
    public Map<EstadoDeEnvio, Long> contarPorEstado() {
        Map<EstadoDeEnvio, Long> conteo = new EnumMap<>(EstadoDeEnvio.class);
        jdbc.sql("SELECT estado, COUNT(*) AS total FROM correo.envios GROUP BY estado")
                .query((fila, n) -> Map.entry(EstadoDeEnvio.valueOf(fila.getString("estado")), fila.getLong("total")))
                .list()
                .forEach(entrada -> conteo.put(entrada.getKey(), entrada.getValue()));
        return conteo;
    }

    /** En cola o esperando reintento. */
    public long contarPendientes() {
        return jdbc.sql("""
                        SELECT COUNT(*) FROM correo.envios
                         WHERE estado IN ('PENDIENTE', 'ENVIANDO', 'ERROR_REINTENTABLE')
                        """)
                .query(Long.class)
                .single();
    }

    /**
     * Los ultimos movimientos, del mas nuevo al mas viejo, con el
     * destinatario ya enmascarado.
     *
     * @param estado solo los de ese estado; nulo = todos
     */
    public List<EnvioRegistrado> recientes(int ultimos, EstadoDeEnvio estado) {
        String filtro = estado == null ? "" : "WHERE estado = :estado ";
        JdbcClient.StatementSpec consulta = jdbc.sql("""
                        SELECT actualizado_en, destinatario, plantilla, estado, destino, identificador,
                               ultimo_error, intentos
                          FROM correo.envios
                        """ + filtro + "ORDER BY actualizado_en DESC, id LIMIT :ultimos")
                .param("ultimos", ultimos);
        if (estado != null) {
            consulta = consulta.param("estado", estado.name());
        }
        return consulta.query((fila, n) -> new EnvioRegistrado(
                        instante(fila, "actualizado_en"),
                        Enmascarar.direccion(fila.getString("destinatario")),
                        fila.getString("plantilla"),
                        fila.getString("estado"),
                        fila.getString("destino") == null ? "" : fila.getString("destino"),
                        fila.getString("identificador"),
                        fila.getString("ultimo_error"),
                        fila.getInt("intentos")))
                .list();
    }

    /** Borra los envios terminados antes de {@code limite}. @return cuantos */
    public int purgarTerminadosAntesDe(Instant limite) {
        return jdbc.sql("""
                        DELETE FROM correo.envios
                         WHERE estado IN ('ENVIADO', 'DESVIADO', 'FALLIDO', 'OMITIDO')
                           AND actualizado_en < :limite
                        """)
                .param("limite", utc(limite))
                .update();
    }

    private static EnvioEnCola enCola(ResultSet fila, int numero) throws SQLException {
        return new EnvioEnCola(
                fila.getObject("id", UUID.class),
                fila.getString("plantilla"),
                fila.getString("destinatario"),
                fila.getString("asunto"),
                deJson(fila.getString("datos")),
                fila.getInt("intentos"),
                fila.getString("trace_id"),
                instante(fila, "creado_en"));
    }

    private static Instant instante(ResultSet fila, String columna) throws SQLException {
        OffsetDateTime valor = fila.getObject(columna, OffsetDateTime.class);
        return valor == null ? null : valor.toInstant();
    }

    private static OffsetDateTime utc(Instant instante) {
        return instante == null ? null : instante.atOffset(ZoneOffset.UTC);
    }

    static String aJson(Map<String, Object> datos) {
        return JSON.writeValueAsString(datos == null ? Map.of() : datos);
    }

    static Map<String, Object> deJson(String texto) {
        if (texto == null || texto.isBlank()) {
            return Map.of();
        }
        return JSON.readValue(texto, MAPA);
    }
}
