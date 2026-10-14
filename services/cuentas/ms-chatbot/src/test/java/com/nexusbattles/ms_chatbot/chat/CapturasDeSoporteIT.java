package com.nexusbattles.ms_chatbot.chat;

import com.jayway.jsonpath.JsonPath;
import com.nexusbattles.ms_chatbot.chat.moderacion.ModeracionDeContenido;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

import static com.nexusbattles.ms_chatbot.chat.soporte.ImagenesDePrueba.bloque;
import static com.nexusbattles.ms_chatbot.chat.soporte.ImagenesDePrueba.conBloquesTrasLaCabecera;
import static com.nexusbattles.ms_chatbot.chat.soporte.ImagenesDePrueba.conSegmentosTrasElInicio;
import static com.nexusbattles.ms_chatbot.chat.soporte.ImagenesDePrueba.contiene;
import static com.nexusbattles.ms_chatbot.chat.soporte.ImagenesDePrueba.imagen;
import static com.nexusbattles.ms_chatbot.chat.soporte.ImagenesDePrueba.leer;
import static com.nexusbattles.ms_chatbot.chat.soporte.ImagenesDePrueba.segmento;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ms-chatbot.yaml 1.14.0 (§7.4.6 «Opción de adjuntar capturas de pantalla para
 * soporte técnico», D-CHA-12) contra PostgreSQL real con V1-V19: quien abrió
 * la solicitud adjunta hasta tres capturas mientras sigue abierta, se guardan
 * sin metadatos, solo las ven su dueño y un administrador, y se van con la
 * solicitud.
 */
@Testcontainers
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureMockMvc
@DisplayName("Chatbot · capturas de pantalla en las solicitudes de soporte (1.14.0)")
class CapturasDeSoporteIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    private static final String CABECERA = "X-Id-Sesion-Anonima";
    private static final String CONTENIDO = "/chat/tickets/{ticket}/capturas/{captura}";
    private static final String CONTENIDO_ADMIN = "/chatbot/admin/tickets/{ticket}/capturas/{captura}";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    // La lista negra es otro servicio: aqui aprueba todo.
    @MockitoBean
    private ModeracionDeContenido moderacion;

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM chatbot.tickets_soporte");
    }

    // -------------------------------------------------------------- jugador

    @Test
    @DisplayName("jugador: adjunta a su solicitud abierta, se guarda sin metadatos y la ve en «Tus solicitudes»")
    void laDuenaAdjuntaYSeGuardaSinMetadatos() throws Exception {
        UUID ana = UUID.randomUUID();
        String ticket = abrir(jugador(ana));

        byte[] png = conBloquesTrasLaCabecera(imagen("png", 640, 360),
            bloque("tEXt", "Author\0Ana en Bucaramanga".getBytes(ISO_8859_1)),
            bloque("eXIf", "MM\0*GPSLatitude 7.12".getBytes(ISO_8859_1)));
        String captura = capturaId(adjuntar(jugador(ana), ticket, png)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.tipo").value("image/png"))
            .andExpect(jsonPath("$.ancho").value(640))
            .andExpect(jsonPath("$.alto").value(360))
            .andExpect(jsonPath("$.contenido").doesNotExist()));
        byte[] jpeg = conSegmentosTrasElInicio(imagen("jpg", 320, 200),
            segmento(0xE1, "Exif\0\0MM GPSLatitude 7.1193 GPSLongitude -73.1198".getBytes(ISO_8859_1)),
            segmento(0xFE, "hecho por Ana".getBytes(ISO_8859_1)));
        String otra = capturaId(adjuntar(jugador(ana), ticket, jpeg)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.tipo").value("image/jpeg")));

        // Lo guardado no lleva ni el texto, ni el EXIF, ni el comentario, y se sigue viendo.
        byte[] guardada = contenidoGuardado(captura);
        assertThat(contiene(guardada, "Bucaramanga")).isFalse();
        assertThat(contiene(guardada, "GPSLatitude")).isFalse();
        assertThat(leer(guardada).getWidth()).isEqualTo(640);
        byte[] guardadaJpeg = contenidoGuardado(otra);
        assertThat(contiene(guardadaJpeg, "GPSLongitude")).isFalse();
        assertThat(contiene(guardadaJpeg, "hecho por Ana")).isFalse();
        assertThat(leer(guardadaJpeg).getHeight()).isEqualTo(200);

        // «Tus solicitudes»: con sus capturas en orden, sin el contenido.
        mvc.perform(get("/chat/tickets").with(jugador(ana)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].capturas.length()").value(2))
            .andExpect(jsonPath("$[0].capturas[0].capturaId").value(captura))
            .andExpect(jsonPath("$[0].capturas[1].capturaId").value(otra))
            .andExpect(jsonPath("$[0].capturas[1].tipo").value("image/jpeg"))
            .andExpect(jsonPath("$[0].capturas[0].contenido").doesNotExist());

        // El contenido es el guardado, con cabeceras que no dejan ejecutarlo ni guardarlo por el camino.
        mvc.perform(get(CONTENIDO, ticket, captura).with(jugador(ana)))
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/png"))
            .andExpect(content().bytes(guardada))
            .andExpect(header().string("Cache-Control", "no-store, private"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("Content-Security-Policy", "default-src 'none'; sandbox"))
            .andExpect(header().string("Content-Disposition", "inline; filename=\"captura-" + captura + ".png\""));

        // Se van con la solicitud.
        jdbc.update("DELETE FROM chatbot.tickets_soporte WHERE id = ?::uuid", ticket);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chatbot.tickets_soporte_capturas", Integer.class))
            .isZero();
    }

    @Test
    @DisplayName("nadie más: otro jugador u otra sesión reciben 404 como si no existiera; el administrador sí la ve")
    void soloLaDuenaYElAdministrador() throws Exception {
        UUID ana = UUID.randomUUID();
        UUID beto = UUID.randomUUID();
        String ticket = abrir(jugador(ana));
        String captura = capturaId(adjuntar(jugador(ana), ticket, imagen("png", 100, 50))
            .andExpect(status().isCreated()));
        String deBeto = abrir(jugador(beto));

        // Otro jugador: ni la lee, ni adjunta a la solicitud ajena, ni la alcanza por la ruta de la suya.
        mvc.perform(get(CONTENIDO, ticket, captura).with(jugador(beto)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.motivo").value("SOLICITUD_NO_ENCONTRADA"));
        adjuntar(jugador(beto), ticket, imagen("png", 10, 10))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.motivo").value("SOLICITUD_NO_ENCONTRADA"));
        mvc.perform(get(CONTENIDO, deBeto, captura).with(jugador(beto)))
            .andExpect(status().isNotFound());
        // Una sesión de visitante tampoco; sin nadie, 401.
        String sesion = "visitante-" + UUID.randomUUID();
        abrir(visitante(sesion));
        mvc.perform(get(CONTENIDO, ticket, captura).with(visitante(sesion)))
            .andExpect(status().isNotFound());
        mvc.perform(get(CONTENIDO, ticket, captura))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.motivo").value("SESION_REQUERIDA"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chatbot.tickets_soporte_capturas", Integer.class))
            .isEqualTo(1);

        // El administrador la ve en la solicitud y la lee; por la ruta de otra solicitud, no.
        mvc.perform(get("/chatbot/admin/tickets/{ticket}", ticket).with(administrador()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.capturas[0].capturaId").value(captura))
            .andExpect(jsonPath("$.capturas[0].ancho").value(100));
        mvc.perform(get(CONTENIDO_ADMIN, ticket, captura).with(administrador()))
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/png"))
            .andExpect(content().bytes(contenidoGuardado(captura)));
        mvc.perform(get(CONTENIDO_ADMIN, deBeto, captura).with(administrador()))
            .andExpect(status().isNotFound());
        // Un jugador no entra al panel.
        mvc.perform(get(CONTENIDO_ADMIN, ticket, captura)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_JUGADOR"))))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("límites: solo PNG o JPEG bien formados de hasta 2 MB, tres por solicitud y mientras siga abierta")
    void losLimites() throws Exception {
        UUID ana = UUID.randomUUID();
        String ticket = abrir(jugador(ana));
        byte[] png = imagen("png", 10, 10);

        // Otro formato, un SVG, un PNG con una página pegada detrás o con otra cosa dentro: 415.
        byte[] poliglota = Arrays.copyOf(png, png.length + 30);
        System.arraycopy("<html><script>x</script></html>".getBytes(StandardCharsets.US_ASCII), 0, poliglota,
            png.length, 30);
        for (byte[] noAdmitida : new byte[][] {imagen("gif", 10, 10),
            "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"/>".getBytes(StandardCharsets.UTF_8),
            poliglota}) {
            adjuntar(jugador(ana), ticket, noAdmitida)
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.motivo").value("CAPTURA_NO_ADMITIDA"));
        }
        // Más de 2 MB: 413, aunque lo de delante dejara pasar más.
        adjuntar(jugador(ana), ticket, Arrays.copyOf(png, 2 * 1024 * 1024 + 1))
            .andExpect(status().is(413))
            .andExpect(jsonPath("$.motivo").value("CAPTURA_DEMASIADO_GRANDE"));

        // Tres sí; la cuarta, 409.
        for (int i = 0; i < 3; i++) {
            adjuntar(jugador(ana), ticket, png).andExpect(status().isCreated());
        }
        adjuntar(jugador(ana), ticket, png)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.motivo").value("CAPTURAS_COMPLETAS"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chatbot.tickets_soporte_capturas WHERE ticket_id = ?::uuid",
            Integer.class, ticket)).isEqualTo(3);

        // Respondida, ya no admite más, aunque tenga sitio.
        mvc.perform(patch("/chatbot/admin/tickets/{ticket}", ticket).with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"RESUELTO\",\"respuesta\":\"Ya quedó.\"}"))
            .andExpect(status().isOk());
        String otra = abrir(jugador(ana));
        adjuntar(jugador(ana), otra, png).andExpect(status().isCreated());
        mvc.perform(patch("/chatbot/admin/tickets/{ticket}", otra).with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"RESUELTO\",\"respuesta\":\"Listo.\"}"))
            .andExpect(status().isOk());
        adjuntar(jugador(ana), otra, png)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.motivo").value("SOLICITUD_CERRADA"));
        // Lo ya adjunto se sigue viendo después de responderlas: 3 + 1.
        mvc.perform(get("/chat/tickets").with(jugador(ana)))
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[*].capturas[*].capturaId", hasSize(4)));
    }

    // ------------------------------------------------------------ visitante

    @Test
    @DisplayName("visitante: adjunta a la suya con su sesión y solo esa sesión la ve; un token no la alcanza")
    void elVisitanteAdjuntaALaSuya() throws Exception {
        String sesion = "visitante-" + UUID.randomUUID();
        String ticket = abrir(visitante(sesion));
        String captura = capturaId(adjuntar(visitante(sesion), ticket, imagen("png", 64, 32))
            .andExpect(status().isCreated()));

        mvc.perform(get("/chat/tickets").with(visitante(sesion)))
            .andExpect(jsonPath("$[0].capturas[0].capturaId").value(captura));
        mvc.perform(get(CONTENIDO, ticket, captura).with(visitante(sesion)))
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/png"));

        // Con un token la cabecera no cuenta: ese jugador no es el visitante.
        mvc.perform(get(CONTENIDO, ticket, captura).with(jugador(UUID.randomUUID())).with(visitante(sesion)))
            .andExpect(status().isNotFound());
        // Otra sesión registrada, tampoco.
        String otra = "visitante-" + UUID.randomUUID();
        abrir(visitante(otra));
        mvc.perform(get(CONTENIDO, ticket, captura).with(visitante(otra)))
            .andExpect(status().isNotFound());
        adjuntar(visitante(otra), ticket, imagen("png", 8, 8))
            .andExpect(status().isNotFound());
        // El panel la marca como de un visitante, con su captura.
        mvc.perform(get("/chatbot/admin/tickets/{ticket}", ticket).with(administrador()))
            .andExpect(jsonPath("$.deVisitante").value(true))
            .andExpect(jsonPath("$.capturas[0].capturaId").value(captura));
    }

    // ------------------------------------------------------------ auxiliares

    private static RequestPostProcessor jugador(UUID uid) {
        return jwt().jwt(j -> j.claim("uid", uid.toString()));
    }

    private static RequestPostProcessor administrador() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"));
    }

    private static RequestPostProcessor visitante(String sesion) {
        return peticion -> {
            peticion.addHeader(CABECERA, sesion);
            return peticion;
        };
    }

    private String abrir(RequestPostProcessor quien) throws Exception {
        String cuerpo = "{\"categoria\":\"SOPORTE_TECNICO\",\"asunto\":\"La tienda no carga\","
            + "\"mensaje\":\"Se queda en blanco al abrirla.\"}";
        String respuesta = mvc.perform(post("/chat/tickets").with(quien)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(respuesta, "$.ticketId");
    }

    private ResultActions adjuntar(RequestPostProcessor quien, String ticket, byte[] archivo) throws Exception {
        return mvc.perform(multipart("/chat/tickets/{ticket}/capturas", ticket)
            .file(new MockMultipartFile("archivo", "pantallazo de ana.png", "image/png", archivo))
            .with(quien));
    }

    private static String capturaId(ResultActions subida) throws Exception {
        return JsonPath.read(subida.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8),
            "$.capturaId");
    }

    private byte[] contenidoGuardado(String captura) {
        return jdbc.queryForObject("SELECT contenido FROM chatbot.tickets_soporte_capturas WHERE id = ?::uuid",
            byte[].class, captura);
    }
}
