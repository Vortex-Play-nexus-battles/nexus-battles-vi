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
 * Revisión final del 7.4 (ms-chatbot.yaml 1.11.0, 7.4.2 y 7.4.5): la base de
 * conocimiento dice lo que el juego hace hoy. El contenido, con el texto de
 * antes y el de ahora de cada tema, está en
 * {@code db/contenido/V16__base_de_conocimiento_al_dia.json}; la evidencia de
 * cada corrección, en {@code contenido/evidencia-v11.md} (sección V16).
 *
 * <ol>
 *   <li><b>«Cómo apelar una sanción»</b> (clave {@code apelar-sancion}, V11)
 *       se escribió antes de SA-4a y SA-4b (#962, #965) y decía que una
 *       suspensión o un baneo no se podían apelar desde el juego. Hoy se
 *       apelan desde la pantalla de entrada («Apelar esta sanción») y el panel
 *       responde en 15 días hábiles.</li>
 *   <li><b>18 temas creados en DEV</b> desde la consola, fuera del
 *       repositorio, contradecían el código o el documento oficial (auditoría
 *       del 14 de octubre de los 119 temas que el repositorio no tiene).</li>
 * </ol>
 *
 * <p>Nada es heurístico: cada corrección se aplica solo al tema que sigue
 * diciendo <b>exactamente</b> lo auditado (por su clave o por su título, y su
 * respuesta en español). Si un administrador ya lo editó, no se toca. En una
 * base sin esos temas (el banco, una instalación nueva) no cambia nada salvo
 * el tema de V11.
 *
 * <p>Se aplica como V11 y V15, sin perder la historia de versiones (§7.4.10):
 * si en producción hay algo que corregir, esa versión pasa a RETIRADA (se
 * puede revertir desde el panel) y nace una copia suya en PRODUCCIÓN, con las
 * intenciones adicionales de cada tema (V12), sobre la que se corrige. La
 * candidata en edición, si la hay, recibe las mismas correcciones con la misma
 * regla. Se añaden los casos de evaluación que no existieran.
 *
 * <p>Todo lo que usa va copiado aquí: una migración aplicada no puede
 * depender de código de la aplicación que cambie después.
 */
public class V16__base_de_conocimiento_al_dia extends BaseJavaMigration {

    static final String CONTENIDO = "/db/contenido/V16__base_de_conocimiento_al_dia.json";
    static final String DESCRIPCION = "Revision final del 7.4 (V16): la base dice lo que el juego hace hoy.";

    /**
     * Una corrección: el tema se busca por su clave o por su título (uno de
     * los dos) y solo si su respuesta en español es {@code antesEs}.
     * {@code variantesEs}/{@code variantesEn} nulas: se dejan como estén.
     */
    record Correccion(String clave, String titulo, String antesEs, String respuestaEs, String respuestaEn,
                      String variantesEs, String variantesEn) {
    }

    @Override
    public void migrate(Context context) throws Exception {
        JsonNode contenido = leerContenido();
        List<Correccion> correcciones = correcciones(contenido);
        Connection conexion = context.getConnection();
        Optional<UUID> produccion = versionEn(conexion, "PRODUCCION");
        if (produccion.isPresent() && cuantosCorregir(conexion, produccion.get(), correcciones) > 0) {
            UUID nueva = publicarCopia(conexion, produccion.get());
            corregir(conexion, nueva, correcciones);
        }
        Optional<UUID> borrador = versionEn(conexion, "BORRADOR");
        if (borrador.isPresent()) {
            corregir(conexion, borrador.get(), correcciones);
        }
        agregarCasos(conexion, contenido.path("casos"));
    }

    private static JsonNode leerContenido() throws IOException {
        try (InputStream entrada = V16__base_de_conocimiento_al_dia.class.getResourceAsStream(CONTENIDO)) {
            if (entrada == null) {
                throw new IllegalStateException("Falta el recurso " + CONTENIDO);
            }
            return JsonMapper.builder().build().readTree(entrada);
        }
    }

    static List<Correccion> correcciones(JsonNode contenido) {
        List<Correccion> correcciones = new ArrayList<>();
        for (JsonNode nodo : contenido.path("porClave")) {
            correcciones.add(new Correccion(texto(nodo, "clave"), null, texto(nodo, "antesEs"),
                texto(nodo, "respuestaEs"), texto(nodo, "respuestaEn"),
                nodo.has("variantesEs") ? unir(lista(nodo.path("variantesEs"))) : null,
                nodo.has("variantesEn") ? unir(lista(nodo.path("variantesEn"))) : null));
        }
        for (JsonNode nodo : contenido.path("porTitulo")) {
            correcciones.add(new Correccion(null, texto(nodo, "titulo"), texto(nodo, "antesEs"),
                texto(nodo, "respuestaEs"), texto(nodo, "respuestaEn"), null, null));
        }
        for (Correccion correccion : correcciones) {
            if ((correccion.clave() == null) == (correccion.titulo() == null) || correccion.antesEs() == null
                || correccion.respuestaEs() == null || correccion.respuestaEn() == null) {
                throw new IllegalStateException("Correccion incompleta en " + CONTENIDO + ": " + correccion);
            }
        }
        return correcciones;
    }

    private static String texto(JsonNode nodo, String campo) {
        JsonNode valor = nodo.get(campo);
        return valor == null || valor.isNull() ? null : valor.asString();
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

    // Los temas de esa version que siguen diciendo exactamente lo auditado.
    private static List<UUID> temasQueDicen(Connection conexion, UUID version, Correccion correccion)
            throws SQLException {
        String porDonde = correccion.clave() != null ? "clave" : "titulo";
        List<UUID> temas = new ArrayList<>();
        try (PreparedStatement select = conexion.prepareStatement("SELECT id FROM temas_conocimiento "
                + "WHERE version_id = ? AND " + porDonde + " = ? AND contenido_respuesta_es = ?")) {
            select.setObject(1, version);
            select.setString(2, correccion.clave() != null ? correccion.clave() : correccion.titulo());
            select.setString(3, correccion.antesEs());
            try (ResultSet filas = select.executeQuery()) {
                while (filas.next()) {
                    temas.add(filas.getObject(1, UUID.class));
                }
            }
        }
        return temas;
    }

    private static int cuantosCorregir(Connection conexion, UUID version, List<Correccion> correcciones)
            throws SQLException {
        int cuantos = 0;
        for (Correccion correccion : correcciones) {
            cuantos += temasQueDicen(conexion, version, correccion).size();
        }
        return cuantos;
    }

    private static void corregir(Connection conexion, UUID version, List<Correccion> correcciones)
            throws SQLException {
        for (Correccion correccion : correcciones) {
            for (UUID tema : temasQueDicen(conexion, version, correccion)) {
                ejecutar(conexion, "UPDATE temas_conocimiento SET contenido_respuesta_es = ?, "
                        + "contenido_respuesta_en = ?, palabras_clave_es = COALESCE(?, palabras_clave_es), "
                        + "palabras_clave_en = COALESCE(?, palabras_clave_en) WHERE id = ?",
                    correccion.respuestaEs(), correccion.respuestaEn(), correccion.variantesEs(),
                    correccion.variantesEn(), tema);
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
