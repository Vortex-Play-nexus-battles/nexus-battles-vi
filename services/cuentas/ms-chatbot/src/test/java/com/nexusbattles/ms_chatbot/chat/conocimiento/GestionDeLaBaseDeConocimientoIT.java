package com.nexusbattles.ms_chatbot.chat.conocimiento;

import com.jayway.jsonpath.JsonPath;
import com.nexusbattles.ms_chatbot.chat.enriquecido.VistaDelChat;
import com.nexusbattles.ms_chatbot.chat.moderacion.ModeracionDeContenido;
import com.nexusbattles.ms_chatbot.chat.motor.MotorRespuestas;
import com.nexusbattles.ms_chatbot.chat.motor.ResultadoMotor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ms-chatbot.yaml 1.6.0 (7.4.10 «Gestión de la base de conocimiento») contra
 * PostgreSQL real con V1-V12 y la base publicada por V11, por la API del panel:
 * las intenciones adicionales viajan por la candidata, el despliegue, la copia
 * a la siguiente candidata y la exportación e importación; un título repetido
 * es 409 y una importación de más de 500 temas es 413, sin escribir nada.
 */
@Testcontainers
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureMockMvc
@DisplayName("Chatbot · gestion de la base de conocimiento contra PostgreSQL (V12, 1.6.0)")
class GestionDeLaBaseDeConocimientoIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    private static final String RUTA = "/chatbot/admin/base-conocimiento";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MotorRespuestas motor;

    // La publicacion programada de verdad corre cada minuto: aqui no.
    @MockitoBean
    private DespliegueProgramadoTarea tareaProgramada;

    // La lista negra es otro servicio (no se usa aqui).
    @MockitoBean
    private ModeracionDeContenido moderacion;

    @BeforeEach
    void candidataNueva() throws Exception {
        if (contarVersiones("BORRADOR") > 0) {
            mvc.perform(delete(RUTA + "/borrador").with(administrador())).andExpect(status().isNoContent());
        }
        mvc.perform(post(RUTA + "/borrador").with(administrador())).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("V12: las intenciones se borran con su tema y la base no acepta repetidas ni inventadas; revision empieza en 0")
    void esquemaDeV12() throws Exception {
        String id = agregar(tema("Tema de prueba de V12", "SOPORTE_TECNICO", "[\"FAQ_GENERAL\", \"PRODUCTO\"]",
            "prueba de esquema doce"));
        UUID temaId = UUID.fromString(id);
        assertThat(intencionesEnLaBase(temaId)).containsExactlyInAnyOrder("FAQ_GENERAL", "PRODUCTO");

        assertThatThrownBy(() -> jdbc.update(
            "INSERT INTO chatbot.intenciones_adicionales_de_tema (tema_id, categoria) VALUES (?, 'PRODUCTO')", temaId))
            .as("la clave primaria no deja repetir una intencion").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
            "INSERT INTO chatbot.intenciones_adicionales_de_tema (tema_id, categoria) VALUES (?, 'NO_EXISTE')", temaId))
            .as("el CHECK no deja una intencion inventada").isInstanceOf(DataIntegrityViolationException.class);

        mvc.perform(delete(RUTA + "/borrador/temas/" + id).with(administrador())).andExpect(status().isNoContent());
        assertThat(intencionesEnLaBase(temaId)).as("se fueron con su tema").isEmpty();

        // ON DELETE CASCADE tambien desde la base, sin pasar por JPA.
        String otro = agregar(tema("Otro tema de prueba de V12", "SOPORTE_TECNICO", "[\"PRODUCTO\"]",
            "otra prueba de esquema doce"));
        jdbc.update("DELETE FROM chatbot.temas_conocimiento WHERE id = ?", UUID.fromString(otro));
        assertThat(intencionesEnLaBase(UUID.fromString(otro))).isEmpty();

        Map<String, Object> revision = jdbc.queryForMap("SELECT column_default, is_nullable, data_type "
            + "FROM information_schema.columns WHERE table_schema = 'chatbot' "
            + "AND table_name = 'versiones_base_conocimiento' AND column_name = 'revision'");
        assertThat(revision).containsEntry("column_default", "0").containsEntry("is_nullable", "NO")
            .containsEntry("data_type", "bigint");
    }

    @Test
    @DisplayName("las intenciones pasan por la candidata, la exportacion e importacion, el despliegue y la copia siguiente")
    void lasIntencionesSobrevivenTodoElCiclo() throws Exception {
        String id = agregar(tema("Zarigüeya intergaláctica", "CUENTA_Y_REGISTRO",
            "[\"SUBASTA_Y_COMERCIO\", \"PRODUCTO\"]", "zarigueya intergalactica"));
        String clave = unico(JsonPath.read(listarTemas(), "$[?(@.id == '" + id + "')].clave"));

        // En el orden del contrato (el del enum), no en el que se mandaron.
        assertThat(intencionesEnLaLista(listarTemas(), clave)).containsExactly("PRODUCTO", "SUBASTA_Y_COMERCIO");

        // Exportar la candidata y volver a importarla tal cual: la misma clave
        // y las mismas intenciones.
        String exportacion = mvc.perform(get(RUTA + "/versiones/" + idCandidata() + "/exportacion")
                .with(administrador()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(JsonPath.<List<Object>>read(exportacion,
            "$.temas[?(@.clave == '" + clave + "')].intencionesAdicionales[*]"))
            .containsExactly("PRODUCTO", "SUBASTA_Y_COMERCIO");
        mvc.perform(put(RUTA + "/borrador/importacion").with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content(exportacion))
            .andExpect(status().isOk());
        assertThat(intencionesEnLaLista(listarTemas(), clave)).containsExactly("PRODUCTO", "SUBASTA_Y_COMERCIO");

        // Desplegar: el tema llega a produccion con sus intenciones, y el
        // motor lo cuenta como de la vista de Subastas.
        mvc.perform(post(RUTA + "/borrador/despliegue").with(administrador())).andExpect(status().isOk());
        assertThat(intencionesEnLaVersion("PRODUCCION", clave)).containsExactlyInAnyOrder("PRODUCTO",
            "SUBASTA_Y_COMERCIO");
        ResultadoMotor enSubastas = motor.generarRespuesta("zarigueya intergalactica", VistaDelChat.SUBASTAS);
        assertThat(enSubastas.temaClave()).isEqualTo(clave);

        // La candidata siguiente es una copia de produccion: la copia del tema
        // (copiarA) atiende lo mismo, en sus propias filas.
        mvc.perform(post(RUTA + "/borrador").with(administrador())).andExpect(status().isCreated());
        assertThat(intencionesEnLaLista(listarTemas(), clave)).containsExactly("PRODUCTO", "SUBASTA_Y_COMERCIO");
        assertThat(intencionesEnLaVersion("BORRADOR", clave)).hasSize(2);
        assertThat(intencionesEnLaVersion("PRODUCCION", clave)).hasSize(2);
    }

    @Test
    @DisplayName("un titulo repetido (sin tildes ni mayusculas) es 409 TEMA_DUPLICADO al agregar, editar e importar, sin escribir nada")
    void tituloRepetidoEs409() throws Exception {
        int temasAntes = contarTemasDeLaCandidata();
        String heroes = unico(JsonPath.read(listarTemas(), "$[?(@.clave == 'heroes-disponibles')].id"));

        mvc.perform(post(RUTA + "/borrador/temas").with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content(tema("¿HEROES disponibles?", "PRODUCTO", "[]", "otra pregunta de heroes")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.motivo").value("TEMA_DUPLICADO"))
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("es el mismo que el del tema")));

        String otro = agregar(tema("Un tema con titulo libre", "FAQ_GENERAL", "[]", "pregunta libre"));
        mvc.perform(put(RUTA + "/borrador/temas/" + otro).with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content(tema("heroes disponibles", "FAQ_GENERAL", "[]", "pregunta libre")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.motivo").value("TEMA_DUPLICADO"));
        // Editar el propio tema con su mismo titulo no choca consigo mismo.
        mvc.perform(put(RUTA + "/borrador/temas/" + heroes).with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content(tema("Héroes disponibles", "PRODUCTO", "[\"FAQ_GENERAL\"]", "heroes")))
            .andExpect(status().isOk());

        String archivo = "{\"temas\": [" + tema("Pujar", "SUBASTA_Y_COMERCIO", "[]", "pujar") + ", "
            + tema("¡pujar!", "SUBASTA_Y_COMERCIO", "[]", "como pujo") + "]}";
        mvc.perform(put(RUTA + "/borrador/importacion").with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content(archivo))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.motivo").value("TEMA_DUPLICADO"))
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("Los temas 1 y 2 del archivo")));

        assertThat(contarTemasDeLaCandidata()).as("la importacion rechazada no borro nada").isEqualTo(temasAntes + 1);
        assertThat(JsonPath.<List<Object>>read(listarTemas(), "$[?(@.id == '" + otro + "')].titulo"))
            .containsExactly("Un tema con titulo libre");
    }

    @Test
    @DisplayName("las variantes repetidas de un tema se guardan una vez, sin error")
    void variantesRepetidasSeGuardanUnaVez() throws Exception {
        String cuerpo = tema("Pregunta con variantes repetidas", "FAQ_GENERAL", "[]", "x")
            .replace("\"variantesEs\": [\"x\"]",
                "\"variantesEs\": [\"Pregunta única\", \"pregunta UNICA\", \"otra forma\", \"Otra forma?\"]");

        String creado = mvc.perform(post(RUTA + "/borrador/temas").with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(JsonPath.<List<Object>>read(creado, "$.variantesEs")).containsExactly("Pregunta única", "otra forma");
    }

    @Test
    @DisplayName("mas de 500 temas o mas de 8 MB es 413 IMPORTACION_DEMASIADO_GRANDE sin tocar la candidata; un archivo anterior a 1.6.0 entra")
    void limitesDeLaImportacion() throws Exception {
        int temasAntes = contarTemasDeLaCandidata();
        String quinientosUno = IntStream.rangeClosed(1, ImportacionDemasiadoGrandeException.MAXIMO_DE_TEMAS + 1)
            .mapToObj(i -> tema("Tema " + i, "FAQ_GENERAL", "[]", "pregunta " + i))
            .collect(Collectors.joining(", ", "{\"temas\": [", "]}"));

        mvc.perform(put(RUTA + "/borrador/importacion").with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content(quinientosUno))
            .andExpect(status().isContentTooLarge())
            .andExpect(jsonPath("$.motivo").value("IMPORTACION_DEMASIADO_GRANDE"))
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("501 temas")));

        byte[] enorme = new byte[(int) ImportacionDemasiadoGrandeException.MAXIMO_DE_BYTES + 1];
        mvc.perform(put(RUTA + "/borrador/importacion").with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content(enorme))
            .andExpect(status().isContentTooLarge())
            .andExpect(jsonPath("$.motivo").value("IMPORTACION_DEMASIADO_GRANDE"));
        assertThat(contarTemasDeLaCandidata()).isEqualTo(temasAntes);

        // El archivo de importacion versionado (sin `intencionesAdicionales`,
        // anterior a 1.6.0) entra igual: 59 temas, ninguno con intenciones.
        String anterior = Files.readString(Path.of("contenido/base-de-conocimiento.json"));
        assertThat(anterior).doesNotContain("intencionesAdicionales");
        mvc.perform(put(RUTA + "/borrador/importacion").with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content(anterior))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(59));
        assertThat(contarTemasDeLaCandidata()).isEqualTo(59);
        assertThat(JsonPath.<List<Object>>read(listarTemas(), "$[*].intencionesAdicionales[*]")).isEmpty();
    }

    @Test
    @DisplayName("las intenciones invalidas son 400 con el motivo en espanol")
    void intencionesInvalidasSon400() throws Exception {
        mvc.perform(post(RUTA + "/borrador/temas").with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content(tema("Tema con su propia categoria", "PRODUCTO", "[\"PRODUCTO\"]", "propia categoria")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(
                "La intencion PRODUCTO ya es la categoria principal del tema; no se repite como adicional."));
    }

    // ------------------------------------------------------------- ayudas

    private static RequestPostProcessor administrador() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"));
    }

    private static String tema(String titulo, String categoria, String intenciones, String variante) {
        return """
            {
              "categoria": "%s",
              "intencionesAdicionales": %s,
              "tipoRespuesta": "DIRECTA",
              "titulo": "%s",
              "variantesEs": ["%s"],
              "respuestaEs": "Respuesta de prueba.",
              "prioridad": 0
            }
            """.formatted(categoria, intenciones, titulo, variante);
    }

    private String agregar(String cuerpo) throws Exception {
        String respuesta = mvc.perform(post(RUTA + "/borrador/temas").with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(respuesta, "$.id");
    }

    private String listarTemas() throws Exception {
        return mvc.perform(get(RUTA + "/borrador/temas").with(administrador()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static String unico(List<Object> valores) {
        assertThat(valores).hasSize(1);
        return String.valueOf(valores.get(0));
    }

    private static List<Object> intencionesEnLaLista(String temas, String clave) {
        return JsonPath.read(temas, "$[?(@.clave == '" + clave + "')].intencionesAdicionales[*]");
    }

    private List<String> intencionesEnLaBase(UUID temaId) {
        return jdbc.queryForList("SELECT categoria FROM chatbot.intenciones_adicionales_de_tema WHERE tema_id = ?",
            String.class, temaId);
    }

    private List<String> intencionesEnLaVersion(String estado, String clave) {
        return jdbc.queryForList("SELECT i.categoria FROM chatbot.intenciones_adicionales_de_tema i "
            + "JOIN chatbot.temas_conocimiento t ON t.id = i.tema_id "
            + "JOIN chatbot.versiones_base_conocimiento v ON v.id = t.version_id "
            + "WHERE v.estado = ? AND t.clave = ?", String.class, estado, clave);
    }

    private int contarVersiones(String estado) {
        Integer cuantas = jdbc.queryForObject(
            "SELECT count(*) FROM chatbot.versiones_base_conocimiento WHERE estado = ?", Integer.class, estado);
        return cuantas == null ? 0 : cuantas;
    }

    private int contarTemasDeLaCandidata() {
        Integer cuantos = jdbc.queryForObject("SELECT count(*) FROM chatbot.temas_conocimiento t "
            + "JOIN chatbot.versiones_base_conocimiento v ON v.id = t.version_id WHERE v.estado = 'BORRADOR'",
            Integer.class);
        return cuantos == null ? 0 : cuantos;
    }

    private UUID idCandidata() {
        return jdbc.queryForObject("SELECT id FROM chatbot.versiones_base_conocimiento WHERE estado = 'BORRADOR'",
            UUID.class);
    }
}
