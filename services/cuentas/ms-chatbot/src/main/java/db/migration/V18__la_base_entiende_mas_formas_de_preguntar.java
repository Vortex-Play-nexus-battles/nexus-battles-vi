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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Revisión final del 7.4, segunda auditoría (ms-chatbot.yaml 1.12.0, 7.4.1 y
 * 7.4.2): la base entiende más formas de preguntar. El contenido está en
 * {@code db/contenido/V18__la_base_entiende_mas_formas_de_preguntar.json}.
 *
 * <ul>
 *   <li><b>Dos temas nuevos</b> que la base no tenía: «Términos del servicio y
 *       reglas de uso» (7.4.1 «políticas de uso y términos del servicio»; el
 *       juego no publica un documento aparte y el tema lo dice) y «Cómo
 *       funciona la Subasta», un resumen de lo ya verificado en los temas de
 *       subastas de V11. Solo se crean si su clave no existe.</li>
 *   <li><b>Variantes que faltaban</b> en temas que ya existen («¿de qué formas
 *       puedo jugar?», «the game won't load»). Se suman a las que haya; ninguna
 *       se quita, así que lo que un administrador añadió se conserva.</li>
 *   <li><b>Casos de evaluación con erratas</b> («como me registor», «como
 *       funsionan las subatsas»): la base publicada tiene que resolverlos.</li>
 * </ul>
 *
 * <p>Se aplica como V11 y V15, sin perder la historia de versiones (§7.4.10):
 * si en producción hay algo que cambiar, esa versión pasa a RETIRADA y nace una
 * copia suya en PRODUCCIÓN (con las intenciones adicionales de V12) con los
 * cambios; la candidata en edición, si la hay, recibe los mismos.
 *
 * <p>Todo lo que usa va copiado aquí: una migración aplicada no puede depender
 * de código de la aplicación que cambie después.
 */
public class V18__la_base_entiende_mas_formas_de_preguntar extends BaseJavaMigration {

    static final String CONTENIDO = "/db/contenido/V18__la_base_entiende_mas_formas_de_preguntar.json";
    static final String DESCRIPCION = "Revision final del 7.4 (V18): la base entiende mas formas de preguntar.";

    record Tema(String clave, String categoria, String tipoRespuesta, String titulo, String palabrasEs,
                String palabrasEn, String respuestaEs, String respuestaEn, int prioridad) {
    }

    record Variantes(String clave, List<String> es, List<String> en) {
    }

    @Override
    public void migrate(Context context) throws Exception {
        aplicar(context.getConnection(), leerContenido());
    }

    // Lo que hace la migracion con un contenido dado: las pruebas le pasan
    // otro para los casos que el del repositorio no tiene. Se lee y se valida
    // todo antes de escribir nada: un contenido incompleto no se aplica a medias.
    static void aplicar(Connection conexion, JsonNode contenido) throws SQLException {
        List<Tema> nuevos = temasNuevos(contenido);
        List<Variantes> variantes = variantesNuevas(contenido);

        Optional<UUID> produccion = versionEn(conexion, "PRODUCCION");
        if (produccion.isPresent() && hayCambios(conexion, produccion.get(), nuevos, variantes)) {
            UUID nueva = publicarCopia(conexion, produccion.get());
            aplicarEn(conexion, nueva, nuevos, variantes);
        }
        Optional<UUID> borrador = versionEn(conexion, "BORRADOR");
        if (borrador.isPresent()) {
            aplicarEn(conexion, borrador.get(), nuevos, variantes);
        }
        agregarCasos(conexion, contenido.path("casos"));
    }

    static List<Tema> temasNuevos(JsonNode contenido) {
        List<Tema> nuevos = new ArrayList<>();
        contenido.path("temasNuevos").forEach(nodo -> nuevos.add(tema(nodo)));
        return nuevos;
    }

    static List<Variantes> variantesNuevas(JsonNode contenido) {
        List<Variantes> variantes = new ArrayList<>();
        contenido.path("variantesNuevas").forEach(nodo -> variantes.add(new Variantes(exigido(nodo, "clave"),
            lista(nodo.path("es")), lista(nodo.path("en")))));
        return variantes;
    }

    private static JsonNode leerContenido() throws IOException {
        try (InputStream entrada = V18__la_base_entiende_mas_formas_de_preguntar.class.getResourceAsStream(CONTENIDO)) {
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

    private static List<String> lista(JsonNode nodo) {
        List<String> valores = new ArrayList<>();
        nodo.forEach(v -> valores.add(v.asString()));
        return valores;
    }

    private static Tema tema(JsonNode nodo) {
        return new Tema(exigido(nodo, "clave"), exigido(nodo, "categoria"), exigido(nodo, "tipoRespuesta"),
            exigido(nodo, "titulo"), unir(lista(nodo.path("variantesEs"))), unir(lista(nodo.path("variantesEn"))),
            exigido(nodo, "respuestaEs"), texto(nodo, "respuestaEn"), nodo.path("prioridad").asInt(0));
    }

    // Lo que la base exige de un tema (y la clave de unas variantes).
    private static String exigido(JsonNode nodo, String campo) {
        String valor = texto(nodo, campo);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Contenido incompleto en V18: falta «" + campo + "»");
        }
        return valor;
    }

    // Las variantes se guardan como un solo texto separado por «, », igual que
    // VariantesDePregunta.unir en la fecha de esta migracion.
    static String unir(List<String> variantes) {
        List<String> limpias = variantes.stream()
            .filter(v -> v != null && !v.isBlank())
            .map(String::trim)
            .toList();
        return limpias.isEmpty() ? null : String.join(", ", limpias);
    }

    // Las variantes que ya tiene el tema mas las que faltan, sin repetir
    // (comparadas sin mayusculas): ninguna se quita.
    static String sumar(String actuales, List<String> nuevas) {
        Set<String> vistas = new LinkedHashSet<>();
        List<String> todas = new ArrayList<>();
        for (String variante : (actuales == null ? "" : actuales).split(",")) {
            if (!variante.isBlank() && vistas.add(variante.trim().toLowerCase(Locale.ROOT))) {
                todas.add(variante.trim());
            }
        }
        for (String variante : nuevas) {
            if (variante != null && !variante.isBlank() && vistas.add(variante.trim().toLowerCase(Locale.ROOT))) {
                todas.add(variante.trim());
            }
        }
        return unir(todas);
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

    private record Palabras(UUID id, String es, String en) {
    }

    private static Optional<Palabras> palabrasDe(Connection conexion, UUID version, String clave) throws SQLException {
        try (PreparedStatement select = conexion.prepareStatement("SELECT id, palabras_clave_es, palabras_clave_en "
                + "FROM temas_conocimiento WHERE version_id = ? AND clave = ?")) {
            select.setObject(1, version);
            select.setString(2, clave);
            try (ResultSet filas = select.executeQuery()) {
                return filas.next()
                    ? Optional.of(new Palabras(filas.getObject(1, UUID.class), filas.getString(2), filas.getString(3)))
                    : Optional.empty();
            }
        }
    }

    private static boolean hayCambios(Connection conexion, UUID version, List<Tema> nuevos, List<Variantes> variantes)
            throws SQLException {
        for (Tema tema : nuevos) {
            if (palabrasDe(conexion, version, tema.clave()).isEmpty()) {
                return true;
            }
        }
        for (Variantes suma : variantes) {
            Optional<Palabras> actuales = palabrasDe(conexion, version, suma.clave());
            if (actuales.isPresent() && (!igual(actuales.get().es(), sumar(actuales.get().es(), suma.es()))
                    || !igual(actuales.get().en(), sumar(actuales.get().en(), suma.en())))) {
                return true;
            }
        }
        return false;
    }

    private static boolean igual(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static void aplicarEn(Connection conexion, UUID version, List<Tema> nuevos, List<Variantes> variantes)
            throws SQLException {
        for (Tema tema : nuevos) {
            if (palabrasDe(conexion, version, tema.clave()).isPresent()) {
                continue;
            }
            ejecutar(conexion, "INSERT INTO temas_conocimiento (id, categoria, tipo_respuesta, titulo, palabras_clave_es, "
                    + "palabras_clave_en, contenido_respuesta_es, contenido_respuesta_en, activo, version_id, clave, prioridad) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?, ?)",
                UUID.randomUUID(), tema.categoria(), tema.tipoRespuesta(), tema.titulo(), tema.palabrasEs(),
                tema.palabrasEn(), tema.respuestaEs(), tema.respuestaEn(), version, tema.clave(), tema.prioridad());
        }
        for (Variantes suma : variantes) {
            Optional<Palabras> actuales = palabrasDe(conexion, version, suma.clave());
            if (actuales.isEmpty()) {
                continue;
            }
            String es = sumar(actuales.get().es(), suma.es());
            String en = sumar(actuales.get().en(), suma.en());
            if (!igual(es, actuales.get().es()) || !igual(en, actuales.get().en())) {
                ejecutar(conexion, "UPDATE temas_conocimiento SET palabras_clave_es = ?, palabras_clave_en = ? WHERE id = ?",
                    es, en, actuales.get().id());
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
