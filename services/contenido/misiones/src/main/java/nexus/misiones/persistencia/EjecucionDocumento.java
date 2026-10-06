package nexus.misiones.persistencia;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import nexus.misiones.dominio.Ejecucion;
import nexus.misiones.dominio.Escalon;
import nexus.misiones.dominio.EstadoDePaso;
import nexus.misiones.dominio.EstadoEjecucion;
import nexus.misiones.dominio.HeroeEnMision;
import nexus.misiones.dominio.PasoDeLiquidacion;
import nexus.misiones.dominio.RecompensasDeEjecucion;
import nexus.misiones.dominio.simulacion.ResultadoDeMision;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Una ejecucion en la coleccion {@code ejecuciones} de la base de misiones.
 *
 * <p>Los indices dicen como se usa la coleccion:
 * <ul>
 *   <li>{@code jugador_inicio}: tablon, historial y matricula leen «las del
 *       jugador», de la mas reciente a la mas antigua;</li>
 *   <li>{@code vencidas} y {@code liquidacion}: la «cola» del trabajo en
 *       segundo plano (7.8.12). {@code proximoIntento} hace de arriendo de la simulacion
 *       y de espera tras un fallo; {@code intentosDeSimulacion} es opcional: lo guardado antes de HU-SIM-007 no lo
 *       trae; tampoco {@code sinPenalizacion}, que solo se escribe (como {@code true}) al cancelar con la
 *       simulacion fallando: lo ausente es una cancelacion con penalizacion;</li>
 *   <li>{@code clave_unica}: la idempotencia de la matricula;</li>
 *   <li>{@code una_en_curso_por_mision}: unico PARCIAL, solo sobre las que
 *       estan en progreso. Es la garantia en base de datos de que dos
 *       matriculas simultaneas no dejan al mismo jugador con la misma mision
 *       dos veces en curso; la de que un heroe no esta en dos sitios la da el
 *       bloqueo del inventario.</li>
 * </ul>
 *
 * <p>La version optimista se lleva a mano en {@code version} (ver
 * {@link RepositorioEjecucionesMongo#guardar}), sin {@code @Version}: asi el
 * documento puede ser un record inmutable y la condicion de la escritura queda
 * escrita donde se lee.
 */
@Document(collection = "ejecuciones")
@CompoundIndexes({
        @CompoundIndex(name = "jugador_inicio", def = "{'jugadorUid': 1, 'iniciadaEn': -1}"),
        @CompoundIndex(name = "vencidas", def = "{'estado': 1, 'terminaEn': 1}"),
        @CompoundIndex(name = "liquidacion", def = "{'liquidacionPendiente': 1, 'proximoIntento': 1}"),
        @CompoundIndex(name = "clave_unica", def = "{'jugadorUid': 1, 'claveIdempotencia': 1}", unique = true,
                partialFilter = "{'claveIdempotencia': {'$exists': true}}"),
        @CompoundIndex(name = "una_en_curso_por_mision", def = "{'jugadorUid': 1, 'misionId': 1}", unique = true,
                partialFilter = "{'estado': 'EN_PROGRESO'}")
})
record EjecucionDocumento(
        @Id String id,
        String misionId,
        String jugadorUid,
        HeroeEnMision heroe,
        List<List<String>> estrategia,
        Escalon escalon,
        Instant iniciadaEn,
        Instant terminaEn,
        long semilla,
        String claveIdempotencia,
        EstadoEjecucion estado,
        Instant terminadaEn,
        ResultadoDeMision resultado,
        RecompensasDeEjecucion recompensas,
        Map<String, String> pasos,
        Map<String, String> motivos,
        int intentosDeLiquidacion,
        Instant proximoIntento,
        String ultimoError,
        Integer nivelAlcanzado,
        Double experienciaAcumulada,
        boolean liquidacionPendiente,
        long version,
        Integer intentosDeSimulacion,
        Boolean sinPenalizacion) {

    /** El documento que se escribe, ya con la version siguiente. */
    static EjecucionDocumento de(Ejecucion e, long version) {
        Map<String, String> pasos = new TreeMap<>();
        e.pasos().forEach((paso, estado) -> pasos.put(paso.name(), estado.name()));
        Map<String, String> motivos = new TreeMap<>();
        e.motivos().forEach((paso, motivo) -> motivos.put(paso.name(), motivo));
        return new EjecucionDocumento(e.id().toString(), e.misionId(), e.jugadorUid(), e.heroe(), e.estrategia(),
                e.escalon(), e.iniciadaEn(), e.terminaEn(), e.semilla(), e.claveIdempotencia(), e.estado(),
                e.terminadaEn(), e.resultado(), e.recompensas(), pasos, motivos, e.intentosDeLiquidacion(),
                e.proximoIntento(), e.ultimoError(), e.nivelAlcanzado(), e.experienciaAcumulada(),
                e.liquidacionPendiente(), version, e.intentosDeSimulacion(),
                e.canceladaSinPenalizacion() ? Boolean.TRUE : null);
    }

    Ejecucion aDominio() {
        Ejecucion.Estado s = new Ejecucion.Estado();
        s.id = UUID.fromString(id);
        s.misionId = misionId;
        s.jugadorUid = jugadorUid;
        s.heroe = heroe;
        s.estrategia = estrategia;
        s.escalon = escalon;
        s.iniciadaEn = iniciadaEn;
        s.terminaEn = terminaEn;
        s.semilla = semilla;
        s.claveIdempotencia = claveIdempotencia;
        s.estado = estado;
        s.terminadaEn = terminadaEn;
        s.resultado = resultado;
        s.recompensas = recompensas;
        Map<PasoDeLiquidacion, EstadoDePaso> pasosDelDominio = new EnumMap<>(PasoDeLiquidacion.class);
        if (pasos != null) {
            pasos.forEach((paso, estado) -> conocido(paso).ifPresent(p ->
                    pasosDelDominio.put(p, EstadoDePaso.valueOf(estado))));
        }
        s.pasos = pasosDelDominio;
        Map<PasoDeLiquidacion, String> motivosDelDominio = new EnumMap<>(PasoDeLiquidacion.class);
        if (motivos != null) {
            motivos.forEach((paso, motivo) -> conocido(paso).ifPresent(p -> motivosDelDominio.put(p, motivo)));
        }
        s.motivos = motivosDelDominio;
        s.intentosDeLiquidacion = intentosDeLiquidacion;
        s.proximoIntento = proximoIntento;
        s.ultimoError = ultimoError;
        s.nivelAlcanzado = nivelAlcanzado;
        s.experienciaAcumulada = experienciaAcumulada;
        s.intentosDeSimulacion = intentosDeSimulacion;
        s.sinPenalizacion = sinPenalizacion;
        s.version = version;
        return Ejecucion.reconstruir(s);
    }

    /**
     * Un paso que esta version no conoce —lo escribio una version mas nueva
     * del servicio y despues se volvio a esta (reversion del despliegue)— se
     * ignora en vez de tumbar la lectura: la ejecucion se sigue viendo y
     * liquidando con los pasos que si conoce. Antes, un nombre desconocido
     * hacia fallar {@code valueOf} y con el el reporte y el trabajo.
     */
    static Optional<PasoDeLiquidacion> conocido(String paso) {
        try {
            return Optional.of(PasoDeLiquidacion.valueOf(paso));
        } catch (IllegalArgumentException | NullPointerException desconocido) {
            return Optional.empty();
        }
    }
}
