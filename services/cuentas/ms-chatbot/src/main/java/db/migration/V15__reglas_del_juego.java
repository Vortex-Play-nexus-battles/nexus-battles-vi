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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Revisión final del 7.4 (ms-chatbot.yaml 1.11.0, 7.4.2 «información sobre
 * reglas del juego»): «¿cuáles son las reglas del juego?», «¿cómo se juega?»
 * o «what are the game rules?» no tenían respuesta, o caían en el tema de la
 * IA o en el de las normas de conducta. Un tema nuevo, «Reglas básicas del
 * juego», resume lo ya verificado en los temas de V11 (combate por turnos,
 * equipamiento, modos de batalla, torneo, derrota, normas de conducta; ver
 * {@code contenido/evidencia-v11.md}). El contenido está en
 * {@code db/contenido/V15__reglas_del_juego.json}.
 *
 * <p>Se aplica como V11, sin perder la historia de versiones (§7.4.10): la
 * versión en producción pasa a RETIRADA y nace una copia suya en PRODUCCIÓN
 * (con sus temas y, desde V12, las intenciones adicionales de cada uno) con el
 * tema nuevo; si hay una candidata en edición, también lo recibe. Si el tema
 * ya existe (por su clave), se actualiza. Se añaden sus casos de evaluación
 * que no existieran.
 *
 * <p>Todo lo que usa va copiado aquí: una migración aplicada no puede
 * depender de código de la aplicación que cambie después.
 */
public class V15__reglas_del_juego extends BaseJavaMigration {

    static final String CONTENIDO = "/db/contenido/V15__reglas_del_juego.json";
    static final String DESCRIPCION = "Revision final del 7.4 (V15): tema «Reglas basicas del juego».";

    private record Tema(String clave, String categoria, String tipoRespuesta, String titulo, String palabrasEs,
                        String palabrasEn, String respuestaEs, String respuestaEn, int prioridad, boolean activo) {
    }

    @Override
    public void migrate(Context context) throws Exception {
        JsonNode contenido = leerContenido();
        Tema tema = tema(contenido.path("tema"));
        Connection conexion = context.getConnection();
        Optional<UUID> produccion = versionEn(conexion, "PRODUCCION");
        if (produccion.isEmpty()) {
            return;
        }
        UUID nueva = publicarCopia(conexion, produccion.get());
        ponerTema(conexion, nueva, tema);
        Optional<UUID> borrador = versionEn(conexion, "BORRADOR");
        if (borrador.isPresent()) {
            ponerTema(conexion, borrador.get(), tema);
        }
        agregarCasos(conexion, contenido.path("casos"));
    }

    private static JsonNode leerContenido() throws IOException {
        try (InputStream entrada = V15__reglas_del_juego.class.getResourceAsStream(CONTENIDO)) {
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
            texto(nodo, "titulo"), unir(lista(nodo.path("variantesEs"))), unir(lista(nodo.path("variantesEn"))),
            texto(nodo, "respuestaEs"), texto(nodo, "respuestaEn"), nodo.path("prioridad").asInt(0),
            !nodo.has("activo") || nodo.path("activo").asBoolean(true));
    }

    // Las variantes se guardan como un solo texto separado por «, », igual que
    // VariantesDePregunta.unir en la fecha de esta migracion.
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

    // Retira la de produccion y publica una copia suya como version nueva,
    // con los temas y sus intenciones adicionales (V12).
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
        List<UUID[]> copias = new ArrayList<>();
        try (PreparedStatement select = conexion.prepareStatement(
                "SELECT id FROM temas_conocimiento WHERE version_id = ?")) {
            select.setObject(1, produccion);
            try (ResultSet filas = select.executeQuery()) {
                while (filas.next()) {
                    copias.add(new UUID[] {filas.getObject(1, UUID.class), UUID.randomUUID()});
                }
            }
        }
        for (UUID[] copia : copias) {
            ejecutar(conexion, "INSERT INTO temas_conocimiento (id, categoria, tipo_respuesta, titulo, palabras_clave_es, "
                + "palabras_clave_en, contenido_respuesta_es, contenido_respuesta_en, activo, version_id, clave, prioridad) "
                + "SELECT ?, categoria, tipo_respuesta, titulo, palabras_clave_es, palabras_clave_en, "
                + "contenido_respuesta_es, contenido_respuesta_en, activo, ?, clave, prioridad "
                + "FROM temas_conocimiento WHERE id = ?", copia[1], nueva, copia[0]);
            ejecutar(conexion, "INSERT INTO intenciones_adicionales_de_tema (tema_id, categoria) "
                + "SELECT ?, categoria FROM intenciones_adicionales_de_tema WHERE tema_id = ?", copia[1], copia[0]);
        }
        return nueva;
    }

    // El tema en esa version: se actualiza si su clave ya esta, si no se crea.
    private static void ponerTema(Connection conexion, UUID version, Tema tema) throws SQLException {
        Optional<UUID> existente = Optional.empty();
        try (PreparedStatement select = conexion.prepareStatement(
                "SELECT id FROM temas_conocimiento WHERE version_id = ? AND clave = ?")) {
            select.setObject(1, version);
            select.setString(2, tema.clave());
            try (ResultSet filas = select.executeQuery()) {
                if (filas.next()) {
                    existente = Optional.of(filas.getObject(1, UUID.class));
                }
            }
        }
        if (existente.isPresent()) {
            ejecutar(conexion, "UPDATE temas_conocimiento SET categoria = ?, tipo_respuesta = ?, titulo = ?, "
                    + "palabras_clave_es = ?, palabras_clave_en = ?, contenido_respuesta_es = ?, "
                    + "contenido_respuesta_en = ?, prioridad = ?, activo = ? WHERE id = ?",
                tema.categoria(), tema.tipoRespuesta(), tema.titulo(), tema.palabrasEs(), tema.palabrasEn(),
                tema.respuestaEs(), tema.respuestaEn(), tema.prioridad(), tema.activo(), existente.get());
            return;
        }
        ejecutar(conexion, "INSERT INTO temas_conocimiento (id, categoria, tipo_respuesta, titulo, palabras_clave_es, "
                + "palabras_clave_en, contenido_respuesta_es, contenido_respuesta_en, activo, version_id, clave, prioridad) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(), tema.categoria(), tema.tipoRespuesta(), tema.titulo(), tema.palabrasEs(),
            tema.palabrasEn(), tema.respuestaEs(), tema.respuestaEn(), tema.activo(), version, tema.clave(),
            tema.prioridad());
    }

    private static void agregarCasos(Connection conexion, JsonNode casos) throws SQLException {
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
