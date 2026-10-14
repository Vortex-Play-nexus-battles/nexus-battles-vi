package db.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V18 (1.12.0, segunda auditoría del 7.4): la base entiende más formas de
 * preguntar. Dos temas nuevos y variantes que faltaban, publicados como V11,
 * V15 y V16: versión nueva con la anterior retirada (revertible), la candidata
 * con lo mismo y nada de lo que un administrador ya escribió se pisa.
 *
 * <p>Un contenedor por prueba: cada una parte de una base distinta.
 */
@Testcontainers
@DisplayName("V18 · la base entiende mas formas de preguntar, sin pisar lo que un administrador escribio")
class LaBaseEntiendeMasFormasDePreguntarIT {

    @Container
    PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    private static final String EN_PRODUCCION = "SELECT t.%s FROM temas_conocimiento t JOIN versiones_base_conocimiento v "
        + "ON v.id = t.version_id WHERE v.estado = 'PRODUCCION' AND t.clave = '%s'";

    private Flyway flywayHasta(String version) {
        return Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .schemas("chatbot").defaultSchema("chatbot").createSchemas(true)
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion(version))
            .load();
    }

    private Connection conexion() throws Exception {
        Connection conexion = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
            postgres.getPassword());
        try (Statement sentencia = conexion.createStatement()) {
            sentencia.execute("SET search_path TO chatbot");
        }
        return conexion;
    }

    @Test
    void publicaUnaVersionNuevaConLosTemasYLasVariantesSinPisarLoEscrito() throws Exception {
        flywayHasta("17").migrate();
        UUID candidata = UUID.randomUUID();
        int temasAntes;
        String variantesAntes;
        try (Connection c = conexion()) {
            String produccion = uno(c, "SELECT id FROM versiones_base_conocimiento WHERE estado = 'PRODUCCION'");
            // Un administrador ya sumo una de las variantes, con otras mayusculas.
            ejecutar(c, "UPDATE temas_conocimiento SET palabras_clave_es = palabras_clave_es || ', Formas De Jugar' "
                + "WHERE version_id = ?::uuid AND clave = 'modo-batalla'", produccion);
            variantesAntes = uno(c, String.format(EN_PRODUCCION, "palabras_clave_es", "modo-batalla"));
            // Una intencion adicional: la copia la conserva.
            ejecutar(c, "INSERT INTO intenciones_adicionales_de_tema (tema_id, categoria) SELECT id, 'FAQ_GENERAL' "
                + "FROM temas_conocimiento WHERE version_id = ?::uuid AND clave = 'modo-batalla'", produccion);
            // Una candidata en edicion: con su propio «Terminos del servicio» y modo-batalla.
            ejecutar(c, "INSERT INTO versiones_base_conocimiento (id, numero, estado, descripcion, fecha_creacion) "
                + "VALUES (?, (SELECT MAX(numero) + 1 FROM versiones_base_conocimiento), 'BORRADOR', 'en edicion', ?)",
                candidata, OffsetDateTime.parse("2026-10-14T09:00:00Z"));
            tema(c, candidata.toString(), "terminos-del-servicio", "Términos (redactado en la candidata)",
                "terminos", "Redactado en la candidata.");
            tema(c, candidata.toString(), "modo-batalla", "Modos de batalla", "modos de juego", "Modos.");
            temasAntes = Integer.parseInt(uno(c, "SELECT count(*) FROM temas_conocimiento t JOIN "
                + "versiones_base_conocimiento v ON v.id = t.version_id WHERE v.estado = 'PRODUCCION'"));
        }

        flywayHasta("18").migrate();

        try (Connection c = conexion()) {
            assertThat(uno(c, "SELECT descripcion FROM versiones_base_conocimiento WHERE estado = 'PRODUCCION'"))
                .contains("V18");
            // La de V16 queda retirada, con sus temas: se puede revertir.
            assertThat(uno(c, "SELECT count(*) FROM versiones_base_conocimiento WHERE estado = 'RETIRADA' "
                + "AND descripcion LIKE '%V16%'")).isEqualTo("1");
            assertThat(uno(c, "SELECT count(*) FROM temas_conocimiento t JOIN versiones_base_conocimiento v "
                + "ON v.id = t.version_id WHERE v.estado = 'PRODUCCION'")).isEqualTo(String.valueOf(temasAntes + 2));

            // Los dos temas nuevos, activos y en los dos idiomas.
            assertThat(uno(c, String.format(EN_PRODUCCION, "categoria", "terminos-del-servicio")))
                .isEqualTo("POLITICAS_Y_TERMINOS");
            assertThat(uno(c, String.format(EN_PRODUCCION, "contenido_respuesta_es", "terminos-del-servicio")))
                .contains("no publica hoy un documento de términos y condiciones aparte");
            assertThat(uno(c, String.format(EN_PRODUCCION, "tipo_respuesta", "como-funciona-la-subasta")))
                .isEqualTo("PASO_A_PASO");
            assertThat(uno(c, String.format(EN_PRODUCCION, "contenido_respuesta_en", "como-funciona-la-subasta")))
                .isNotBlank();
            assertThat(uno(c, String.format(EN_PRODUCCION, "activo", "como-funciona-la-subasta"))).isEqualTo("t");

            // Las variantes: las de antes siguen, en su orden, y la repetida no se duplica.
            String variantes = uno(c, String.format(EN_PRODUCCION, "palabras_clave_es", "modo-batalla"));
            assertThat(variantes).startsWith(variantesAntes).contains("de que formas puedo jugar");
            assertThat(variantes.toLowerCase().split("formas de jugar", -1)).hasSize(2);
            assertThat(uno(c, String.format(EN_PRODUCCION, "palabras_clave_en", "problema-tecnico")))
                .contains("won't load", "stuck loading");
            assertThat(textos(c, "SELECT i.categoria FROM intenciones_adicionales_de_tema i JOIN temas_conocimiento t "
                + "ON t.id = i.tema_id JOIN versiones_base_conocimiento v ON v.id = t.version_id "
                + "WHERE v.estado = 'PRODUCCION' AND t.clave = 'modo-batalla'")).containsExactly("FAQ_GENERAL");

            // La candidata: su «Terminos» se queda, recibe la subasta y las variantes.
            String enLaCandidata = "SELECT %s FROM temas_conocimiento WHERE version_id = '" + candidata
                + "' AND clave = '%s'";
            assertThat(uno(c, String.format(enLaCandidata, "contenido_respuesta_es", "terminos-del-servicio")))
                .isEqualTo("Redactado en la candidata.");
            assertThat(uno(c, String.format(enLaCandidata, "count(*)", "como-funciona-la-subasta"))).isEqualTo("1");
            assertThat(uno(c, String.format(enLaCandidata, "palabras_clave_es", "modo-batalla")))
                .startsWith("modos de juego, formas de jugar");

            // Los casos, con su tema esperado.
            assertThat(uno(c, "SELECT tema_clave_esperada FROM casos_evaluacion WHERE pregunta = "
                + "'como funsionan las subatsas'")).isEqualTo("como-funciona-la-subasta");
            assertThat(uno(c, "SELECT count(*) FROM casos_evaluacion WHERE tema_clave_esperada = "
                + "'terminos-del-servicio'")).isEqualTo("3");
        }

        assertThat(flywayHasta("18").migrate().migrationsExecuted).isZero();
    }

    @Test
    void sinNadaQueCambiarNoPublicaUnaVersionNuevaYLosCasosNoSeRepiten() throws Exception {
        flywayHasta("17").migrate();
        String produccion;
        try (Connection c = conexion()) {
            produccion = uno(c, "SELECT id FROM versiones_base_conocimiento WHERE estado = 'PRODUCCION'");
            // Un administrador ya creo los dos temas y sumo todas las variantes.
            tema(c, produccion, "terminos-del-servicio", "Términos", "terminos", "Escrito desde el panel.");
            tema(c, produccion, "como-funciona-la-subasta", "Subasta", "subasta", "Escrito desde el panel.");
            for (V18__la_base_entiende_mas_formas_de_preguntar.Variantes suma
                    : V18__la_base_entiende_mas_formas_de_preguntar.variantesNuevas(contenido())) {
                ejecutar(c, "UPDATE temas_conocimiento SET palabras_clave_es = ?, palabras_clave_en = ? "
                        + "WHERE version_id = ?::uuid AND clave = ?",
                    V18__la_base_entiende_mas_formas_de_preguntar.sumar(
                        uno(c, String.format(EN_PRODUCCION, "palabras_clave_es", suma.clave())), suma.es()),
                    V18__la_base_entiende_mas_formas_de_preguntar.sumar(
                        uno(c, String.format(EN_PRODUCCION, "palabras_clave_en", suma.clave())), suma.en()),
                    produccion, suma.clave());
            }
            // Y uno de los casos ya existe, con otras mayusculas.
            ejecutar(c, "INSERT INTO casos_evaluacion (id, pregunta, tema_clave_esperada, activo, fecha_creacion) "
                + "VALUES (?, 'COMO ME REGISTOR', 'crear-cuenta', TRUE, now())", UUID.randomUUID());
        }

        flywayHasta("18").migrate();

        try (Connection c = conexion()) {
            assertThat(uno(c, "SELECT id FROM versiones_base_conocimiento WHERE estado = 'PRODUCCION'"))
                .isEqualTo(produccion);
            assertThat(uno(c, "SELECT count(*) FROM versiones_base_conocimiento WHERE descripcion LIKE '%V18%'"))
                .isEqualTo("0");
            assertThat(uno(c, String.format(EN_PRODUCCION, "contenido_respuesta_es", "terminos-del-servicio")))
                .isEqualTo("Escrito desde el panel.");
            assertThat(uno(c, "SELECT count(*) FROM casos_evaluacion WHERE lower(pregunta) = 'como me registor'"))
                .isEqualTo("1");
            assertThat(uno(c, "SELECT count(*) FROM casos_evaluacion WHERE pregunta = 'el jeugo no carga'"))
                .isEqualTo("1");
        }
    }

    @Test
    void sinVersionEnProduccionSoloSumaALaCandidataYLosCasosConPregunta() throws Exception {
        flywayHasta("17").migrate();
        UUID candidata = UUID.randomUUID();
        try (Connection c = conexion()) {
            // Sin version en produccion (todas retiradas) y una candidata sin
            // los temas a los que se suman variantes.
            ejecutar(c, "UPDATE versiones_base_conocimiento SET estado = 'RETIRADA' WHERE estado = 'PRODUCCION'");
            ejecutar(c, "INSERT INTO versiones_base_conocimiento (id, numero, estado, descripcion, fecha_creacion) "
                + "VALUES (?, (SELECT MAX(numero) + 1 FROM versiones_base_conocimiento), 'BORRADOR', 'vacia', ?)",
                candidata, OffsetDateTime.parse("2026-10-14T09:00:00Z"));
            int casosAntes = Integer.parseInt(uno(c, "SELECT count(*) FROM casos_evaluacion"));

            V18__la_base_entiende_mas_formas_de_preguntar.aplicar(c, JsonMapper.builder().build().readTree("""
                {"temasNuevos": [{"clave": "tema-de-prueba", "categoria": "FAQ_GENERAL", "tipoRespuesta": "DIRECTA",
                  "titulo": "Tema de prueba", "respuestaEs": "Respuesta.", "variantesEs": ["prueba"]}],
                 "variantesNuevas": [{"clave": "modo-batalla", "es": ["formas de jugar"], "en": []}],
                 "casos": [{"pregunta": "  ", "temaClaveEsperada": "tema-de-prueba"},
                           {"temaClaveEsperada": "tema-de-prueba"},
                           {"pregunta": "una pregunta de prueba", "temaClaveEsperada": "tema-de-prueba"}]}
                """));

            assertThat(uno(c, "SELECT count(*) FROM versiones_base_conocimiento WHERE estado = 'PRODUCCION'"))
                .isEqualTo("0");
            assertThat(textos(c, "SELECT clave FROM temas_conocimiento WHERE version_id = '" + candidata + "'"))
                .containsExactly("tema-de-prueba");
            assertThat(uno(c, "SELECT count(*) FROM casos_evaluacion")).isEqualTo(String.valueOf(casosAntes + 1));
        }
    }

    private static tools.jackson.databind.JsonNode contenido() throws Exception {
        try (var entrada = LaBaseEntiendeMasFormasDePreguntarIT.class
                .getResourceAsStream(V18__la_base_entiende_mas_formas_de_preguntar.CONTENIDO)) {
            return JsonMapper.builder().build().readTree(entrada);
        }
    }

    private static void tema(Connection c, String version, String clave, String titulo, String variantesEs,
                             String respuestaEs) throws Exception {
        ejecutar(c, "INSERT INTO temas_conocimiento (id, categoria, tipo_respuesta, titulo, palabras_clave_es, "
                + "palabras_clave_en, contenido_respuesta_es, contenido_respuesta_en, activo, version_id, clave, "
                + "prioridad) VALUES (?, 'FAQ_GENERAL', 'DIRECTA', ?, ?, NULL, ?, NULL, TRUE, ?::uuid, ?, 0)",
            UUID.randomUUID(), titulo, variantesEs, respuestaEs, version, clave);
    }

    private static void ejecutar(Connection c, String sql, Object... valores) throws Exception {
        try (PreparedStatement sentencia = c.prepareStatement(sql)) {
            for (int i = 0; i < valores.length; i++) {
                sentencia.setObject(i + 1, valores[i]);
            }
            sentencia.executeUpdate();
        }
    }

    private static String uno(Connection c, String sql) throws Exception {
        List<String> valores = textos(c, sql);
        assertThat(valores).hasSize(1);
        return valores.get(0);
    }

    private static List<String> textos(Connection c, String sql) throws Exception {
        List<String> textos = new ArrayList<>();
        try (Statement sentencia = c.createStatement(); ResultSet filas = sentencia.executeQuery(sql)) {
            while (filas.next()) {
                textos.add(filas.getString(1));
            }
        }
        return textos;
    }
}
