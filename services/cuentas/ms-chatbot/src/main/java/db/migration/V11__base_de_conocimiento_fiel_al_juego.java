package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Revisión final del 7.4 (ms-chatbot.yaml 1.5.0): la base de conocimiento dice
 * lo que el juego hace hoy.
 *
 * <p>La auditoría encontró veinte temas que describían cosas que el juego no
 * tiene o tiene distinto (logros e insignias, un sistema de mazos, perder
 * objetos al ser derrotado, la cuenta sin verificar el correo, equipos de seis
 * en el torneo, transmisión en vídeo…, #948) y áreas del §7.4.1 sin ningún
 * tema. El contenido corregido está en
 * {@code db/contenido/V11__base_de_conocimiento_fiel_al_juego.json}, y de dónde
 * sale cada afirmación, en {@code contenido/evidencia-v11.md}.
 *
 * <p>Cómo se aplica, sin perder la historia de versiones (§7.4.10):
 * <ol>
 *   <li>La versión en producción pasa a RETIRADA y nace una nueva versión en
 *       PRODUCCIÓN, copia de ella, con las correcciones aplicadas: desde el
 *       panel se puede revertir a la anterior como a cualquier otra.</li>
 *   <li>Cada corrección busca su tema por el título sembrado en V2 o por la
 *       clave que traía {@code contenido/temas-7-4-2.json} (el archivo de
 *       importación que 1.5.0 reemplaza por {@code contenido/base-de-conocimiento.json});
 *       si lo encuentra, lo reescribe y le da
 *       una clave legible; si no (p. ej. esos catorce temas nunca se
 *       importaron en ese entorno), lo crea. Los temas nuevos se crean, o se
 *       actualizan si la clave ya existía.</li>
 *   <li>Si hay una candidata en edición, recibe las mismas correcciones: si
 *       no, publicarla devolvería los textos equivocados.</li>
 *   <li>Los casos de evaluación y las respuestas ya guardadas que apuntaban
 *       a la clave anterior de un tema de V2 (su UUID) pasan a la clave
 *       nueva: es el mismo tema, así que las analíticas de temas frecuentes
 *       siguen contándolo junto y «más detalle» sobre una respuesta de antes
 *       de la migración lo sigue encontrando. Se añaden los casos nuevos (dos
 *       por tema, en español y en inglés).</li>
 * </ol>
 */
public class V11__base_de_conocimiento_fiel_al_juego extends BaseJavaMigration {

    static final String CONTENIDO = "/db/contenido/V11__base_de_conocimiento_fiel_al_juego.json";
    static final String DESCRIPCION = "Revision final del 7.4 (V11): la base dice lo que el juego hace hoy "
        + "(correcciones, temas nuevos y casos de evaluacion).";

    private record Tema(String clave, String categoria, String tipoRespuesta, String titulo, String palabrasEs,
                        String palabrasEn, String respuestaEs, String respuestaEn, int prioridad, boolean activo) {
    }

    private record Correccion(String porTitulo, String porClave, Tema tema) {
    }

    private record Fila(UUID id, String clave) {
    }

    @Override
    public void migrate(Context context) throws Exception {
        JsonNode contenido = leerContenido();
        List<Correccion> correcciones = new ArrayList<>();
        for (JsonNode nodo : contenido.path("correcciones")) {
            correcciones.add(new Correccion(texto(nodo, "buscarPorTitulo"), texto(nodo, "buscarPorClave"),
                tema(nodo.path("tema"))));
        }
        List<Tema> nuevos = new ArrayList<>();
        for (JsonNode nodo : contenido.path("nuevos")) {
            nuevos.add(tema(nodo));
        }

        Connection conexion = context.getConnection();
        Optional<UUID> produccion = versionEn(conexion, "PRODUCCION");
        if (produccion.isEmpty()) {
            return;
        }
        UUID nueva = publicarCopia(conexion, produccion.get());
        Map<String, String> clavesRenombradas = aplicar(conexion, nueva, correcciones, nuevos);
        Optional<UUID> borrador = versionEn(conexion, "BORRADOR");
        if (borrador.isPresent()) {
            clavesRenombradas.putAll(aplicar(conexion, borrador.get(), correcciones, nuevos));
        }
        actualizarCasos(conexion, clavesRenombradas, contenido.path("casos"));
    }

    private static JsonNode leerContenido() throws IOException {
        try (InputStream entrada = V11__base_de_conocimiento_fiel_al_juego.class.getResourceAsStream(CONTENIDO)) {
            if (entrada == null) {
                throw new IllegalStateException("Falta el recurso " + CONTENIDO);
            }
            return JsonMapper.builder().build().readTree(entrada);
        }
    }

    private static String texto(JsonNode nodo, String campo) {
        JsonNode valor = nodo.get(campo);
        return valor == null || valor.isNull() ? null : valor.asString();
    }

    private static Tema tema(JsonNode nodo) {
        return new Tema(texto(nodo, "clave"), texto(nodo, "categoria"), texto(nodo, "tipoRespuesta"),
            texto(nodo, "titulo"), unir(lista(nodo.path("variantesEs"))),
            unir(lista(nodo.path("variantesEn"))), texto(nodo, "respuestaEs"),
            texto(nodo, "respuestaEn"), nodo.path("prioridad").asInt(0), !nodo.has("activo")
            || nodo.path("activo").asBoolean(true));
    }

    // Las variantes se guardan como un solo texto separado por «, », igual que
    // VariantesDePregunta.unir en la fecha de esta migracion. Va copiado aqui
    // a proposito: una migracion aplicada no puede depender de codigo de la
    // aplicacion que cambie despues, o una base nueva quedaria distinta.
    private static String unir(List<String> variantes) {
        List<String> limpias = variantes.stream()
            .filter(v -> v != null && !v.isBlank())
            .map(String::trim)
            .toList();
        return limpias.isEmpty() ? null : String.join(", ", limpias);
    }

    private static List<String> lista(JsonNode nodo) {
        List<String> valores = new ArrayList<>();
        nodo.forEach(v -> valores.add(v.asString()));
        return valores;
    }

    private static Optional<UUID> versionEn(Connection conexion, String estado) throws SQLException {
        try (PreparedStatement select = conexion.prepareStatement(
                "SELECT id FROM versiones_base_conocimiento WHERE estado = ?")) {
            select.setString(1, estado);
            try (ResultSet filas = select.executeQuery()) {
                return filas.next() ? Optional.of(filas.getObject(1, UUID.class)) : Optional.empty();
            }
        }
    }

    // Retira la de produccion y publica una copia suya como version nueva.
    private static UUID publicarCopia(Connection conexion, UUID produccion) throws SQLException {
        int numero;
        try (PreparedStatement select = conexion.prepareStatement(
                "SELECT COALESCE(MAX(numero), 0) + 1 FROM versiones_base_conocimiento");
             ResultSet filas = select.executeQuery()) {
            filas.next();
            numero = filas.getInt(1);
        }
        UUID nueva = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(Instant.now());
        ejecutar(conexion, "UPDATE versiones_base_conocimiento SET estado = 'RETIRADA' WHERE id = ?", produccion);
        ejecutar(conexion, "INSERT INTO versiones_base_conocimiento (id, numero, estado, descripcion, fecha_creacion, "
            + "fecha_despliegue) VALUES (?, ?, 'PRODUCCION', ?, ?, ?)", nueva, numero, DESCRIPCION, ahora, ahora);
        ejecutar(conexion, "INSERT INTO temas_conocimiento (id, categoria, tipo_respuesta, titulo, palabras_clave_es, "
            + "palabras_clave_en, contenido_respuesta_es, contenido_respuesta_en, activo, version_id, clave, prioridad) "
            + "SELECT gen_random_uuid(), categoria, tipo_respuesta, titulo, palabras_clave_es, palabras_clave_en, "
            + "contenido_respuesta_es, contenido_respuesta_en, activo, ?, clave, prioridad "
            + "FROM temas_conocimiento WHERE version_id = ?", nueva, produccion);
        return nueva;
    }

    // Aplica las correcciones y los temas nuevos a una version. Devuelve las
    // claves que cambiaron (anterior -> nueva).
    private static Map<String, String> aplicar(Connection conexion, UUID version, List<Correccion> correcciones,
                                               List<Tema> nuevos) throws SQLException {
        Map<String, String> renombradas = new LinkedHashMap<>();
        for (Correccion correccion : correcciones) {
            Optional<Fila> fila = correccion.porClave() != null
                ? buscar(conexion, version, "clave", correccion.porClave())
                : buscar(conexion, version, "titulo", correccion.porTitulo());
            if (fila.isEmpty()) {
                fila = buscar(conexion, version, "clave", correccion.tema().clave());
            }
            if (fila.isEmpty()) {
                insertar(conexion, version, correccion.tema());
                continue;
            }
            String clave = correccion.tema().clave();
            Optional<Fila> ocupada = buscar(conexion, version, "clave", clave);
            if (ocupada.isPresent() && !ocupada.get().id().equals(fila.get().id())) {
                // Otro tema de esta version ya usa la clave nueva: se corrige el
                // texto y se conserva la clave que tenia.
                clave = fila.get().clave();
            }
            actualizar(conexion, fila.get().id(), clave, correccion.tema());
            if (!clave.equals(fila.get().clave())) {
                renombradas.put(fila.get().clave(), clave);
            }
        }
        for (Tema tema : nuevos) {
            Optional<Fila> existente = buscar(conexion, version, "clave", tema.clave());
            if (existente.isPresent()) {
                actualizar(conexion, existente.get().id(), tema.clave(), tema);
            } else {
                insertar(conexion, version, tema);
            }
        }
        return renombradas;
    }

    private static Optional<Fila> buscar(Connection conexion, UUID version, String columna, String valor)
        throws SQLException {
        if (valor == null) {
            return Optional.empty();
        }
        String consulta = "titulo".equals(columna)
            ? "SELECT id, clave FROM temas_conocimiento WHERE version_id = ? AND titulo = ? ORDER BY clave LIMIT 1"
            : "SELECT id, clave FROM temas_conocimiento WHERE version_id = ? AND clave = ?";
        try (PreparedStatement select = conexion.prepareStatement(consulta)) {
            select.setObject(1, version);
            select.setString(2, valor);
            try (ResultSet filas = select.executeQuery()) {
                return filas.next()
                    ? Optional.of(new Fila(filas.getObject(1, UUID.class), filas.getString(2)))
                    : Optional.empty();
            }
        }
    }

    private static void actualizar(Connection conexion, UUID id, String clave, Tema tema) throws SQLException {
        ejecutar(conexion, "UPDATE temas_conocimiento SET clave = ?, categoria = ?, tipo_respuesta = ?, titulo = ?, "
                + "palabras_clave_es = ?, palabras_clave_en = ?, contenido_respuesta_es = ?, contenido_respuesta_en = ?, "
                + "prioridad = ?, activo = ? WHERE id = ?",
            clave, tema.categoria(), tema.tipoRespuesta(), tema.titulo(), tema.palabrasEs(), tema.palabrasEn(),
            tema.respuestaEs(), tema.respuestaEn(), tema.prioridad(), tema.activo(), id);
    }

    private static void insertar(Connection conexion, UUID version, Tema tema) throws SQLException {
        ejecutar(conexion, "INSERT INTO temas_conocimiento (id, categoria, tipo_respuesta, titulo, palabras_clave_es, "
                + "palabras_clave_en, contenido_respuesta_es, contenido_respuesta_en, activo, version_id, clave, prioridad) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(), tema.categoria(), tema.tipoRespuesta(), tema.titulo(), tema.palabrasEs(),
            tema.palabrasEn(), tema.respuestaEs(), tema.respuestaEn(), tema.activo(), version, tema.clave(),
            tema.prioridad());
    }

    private static void actualizarCasos(Connection conexion, Map<String, String> clavesRenombradas, JsonNode casos)
        throws SQLException {
        for (Map.Entry<String, String> cambio : clavesRenombradas.entrySet()) {
            ejecutar(conexion, "UPDATE casos_evaluacion SET tema_clave_esperada = ? WHERE tema_clave_esperada = ?",
                cambio.getValue(), cambio.getKey());
            ejecutar(conexion, "UPDATE mensajes SET tema_clave = ? WHERE tema_clave = ?",
                cambio.getValue(), cambio.getKey());
        }
        Timestamp ahora = Timestamp.from(Instant.now());
        for (JsonNode caso : casos) {
            String pregunta = texto(caso, "pregunta");
            String esperada = texto(caso, "temaClaveEsperada");
            if (pregunta == null || pregunta.isBlank() || existeCaso(conexion, pregunta)) {
                continue;
            }
            ejecutar(conexion, "INSERT INTO casos_evaluacion (id, pregunta, tema_clave_esperada, activo, fecha_creacion) "
                + "VALUES (?, ?, ?, TRUE, ?)", UUID.randomUUID(), pregunta, esperada, ahora);
        }
    }

    private static boolean existeCaso(Connection conexion, String pregunta) throws SQLException {
        try (PreparedStatement select = conexion.prepareStatement(
                "SELECT 1 FROM casos_evaluacion WHERE lower(pregunta) = lower(?)")) {
            select.setString(1, pregunta);
            try (ResultSet filas = select.executeQuery()) {
                return filas.next();
            }
        }
    }

    private static void ejecutar(Connection conexion, String sql, Object... valores) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            for (int i = 0; i < valores.length; i++) {
                sentencia.setObject(i + 1, valores[i]);
            }
            sentencia.executeUpdate();
        }
    }
}
