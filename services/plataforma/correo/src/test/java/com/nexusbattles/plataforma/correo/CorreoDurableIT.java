package com.nexusbattles.plataforma.correo;

import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import com.nexusbattles.plataforma.correo.cola.ConfiguracionDeEntrega;
import com.nexusbattles.plataforma.correo.cola.EstadoDeEnvio;
import com.nexusbattles.plataforma.correo.cola.MaquinaDeEstados;
import com.nexusbattles.plataforma.correo.cola.MetricasDeCorreo;
import com.nexusbattles.plataforma.correo.cola.NuevoEnvio;
import com.nexusbattles.plataforma.correo.cola.PoliticaDeReintentos;
import com.nexusbattles.plataforma.correo.cola.RepositorioDeEnvios;
import com.nexusbattles.plataforma.correo.cola.TrabajadorDeEntrega;
import com.nexusbattles.plataforma.correo.envio.ComposicionDeCorreo;
import com.nexusbattles.plataforma.correo.envio.EnviadorCorreoService;
import com.nexusbattles.plataforma.correo.envio.ResultadoDeEntrega;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El correo durable de punta a punta (B1): una PostgreSQL y un Mailpit de
 * verdad, la aplicacion entera y la API con credenciales firmadas de verdad.
 *
 * <p>El trabajador no esta programado ({@code correo.entrega.activa=false}):
 * cada prueba lo mueve a mano, y crea instancias nuevas cuando necesita
 * "reiniciar el servicio" o tener dos trabajando a la vez. El reloj de esas
 * instancias se adelanta a mano: ninguna prueba espera 30 segundos a un
 * reintento.
 *
 * <p>Sin {@code disabledWithoutDocker}, como en salas-partidas: una prueba
 * omitida no es una prueba que pasa.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CorreoDurableIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    static GenericContainer<?> mailpit = new GenericContainer<>(DockerImageName.parse("axllent/mailpit"))
            .withExposedPorts(1025, 8025);

    private static final EmisorDeTokensDePrueba EMISOR = EmisorDeTokensDePrueba.emisor();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("spring.mail.host", mailpit::getHost);
        registro.add("spring.mail.port", () -> mailpit.getMappedPort(1025));
        // Las direcciones reservadas (@nexusbattles.test, RFC 2606) van al
        // buzon de pruebas, que aqui es el mismo Mailpit: es como dev manda
        // las de los canarios al suyo.
        registro.add("correo.buzon-de-pruebas.host", mailpit::getHost);
        registro.add("correo.buzon-de-pruebas.puerto", () -> mailpit.getMappedPort(1025));
        registro.add("correo.entrega.activa", () -> "false");
        registro.add("SMTP_TIMEOUT_MS", () -> "3000");
        EmisorDeTokensDePrueba.registrarJwks(registro);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private RepositorioDeEnvios repositorio;
    @Autowired
    private EnviadorCorreoService enviador;
    @Autowired
    private ComposicionDeCorreo composicion;
    @Autowired
    private MetricasDeCorreo metricas;
    @Autowired
    private PlatformTransactionManager transacciones;
    @Autowired
    private JavaMailSender mailSender;
    @Autowired
    private MeterRegistry medidor;

    /** Cada prueba parte de una cola y una bandeja vacias. */
    @BeforeEach
    void vaciar() throws Exception {
        jdbc.sql("DELETE FROM correo.envios").update();
        HttpResponse<String> respuesta = HTTP.send(
                HttpRequest.newBuilder(URI.create(baseMailpit() + "/api/v1/messages")).DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(respuesta.statusCode()).isEqualTo(200);
    }

    // ------------------------------------------------------------------
    // Aceptar = guardar; el trabajador entrega despues
    // ------------------------------------------------------------------

    @Test
    @DisplayName("un 202 es una fila guardada; el correo sale cuando pasa el trabajador, con enlace y sin dejar el codigo")
    void aceptarEsGuardarYElTrabajadorEntregaDespues() throws Exception {
        mockMvc.perform(comoServicio("/api/v1/correos/confirmacion-cuenta")
                        .header("X-Trace-Id", "4bf92f3577b34da6a3ce929d0e0e4736")
                        .content("""
                                {"email":"nuevo@nexusbattles.test","apodo":"ElGuerrero",
                                 "codigo":"734201","minutosVigencia":15,"proposito":"VERIFICACION"}
                                """))
                .andExpect(status().isAccepted());

        Fila aceptada = unicaFila();
        assertThat(aceptada.estado()).isEqualTo("PENDIENTE");
        assertThat(aceptada.intentos()).isZero();
        assertThat(aceptada.datos()).containsEntry("codigo", "734201");
        assertThat(aceptada.trazaId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(cuantosMensajes()).as("aceptar no es enviar").isZero();

        assertThat(trabajador(Clock.systemUTC()).procesarRonda()).isEqualTo(1);

        Fila entregada = unicaFila();
        assertThat(entregada.estado()).as("direccion reservada: al buzon de pruebas").isEqualTo("DESVIADO");
        assertThat(entregada.destino()).isEqualTo("BUZON_DE_PRUEBAS");
        assertThat(entregada.intentos()).isEqualTo(1);
        assertThat(entregada.identificador()).as("Message-ID para cruzarlo con el servidor").isNotBlank();
        assertThat(entregada.enviadoEn()).isNotNull();
        assertThat(entregada.datos())
                .as("el codigo de un solo uso no sobrevive al envio")
                .doesNotContainKey("codigo")
                .containsEntry("apodo", "ElGuerrero");

        Map<String, Object> mensaje = mensajePara("nuevo@nexusbattles.test");
        assertThat(mensaje.get("Subject")).isEqualTo("Confirma tu cuenta de The Nexus Battles VI");
        assertThat((String) mensaje.get("HTML"))
                .contains("734201")
                .contains("Confirmar mi correo")
                .contains("href=\"http://localhost/verificar#codigo=734201&amp;correo=nuevo%40nexusbattles.test\"");
        assertThat((String) mensaje.get("Text"))
                .as("la version en texto lleva el enlace completo")
                .contains("http://localhost/verificar#codigo=734201&correo=nuevo%40nexusbattles.test");
    }

    @Test
    @DisplayName("la misma Idempotency-Key dos veces encola un solo correo")
    void laMismaIdempotencyKeyNoEncolaDosVeces() throws Exception {
        String cuerpo = """
                {"email":"jugador@nexusbattles.test","apodo":"ElGuerrero"}
                """;
        for (int vez = 0; vez < 2; vez++) {
            mockMvc.perform(comoServicio("/api/v1/correos/bienvenida")
                            .header("Idempotency-Key", "bienvenida-7f3a")
                            .content(cuerpo))
                    .andExpect(status().isAccepted());
        }
        assertThat(filas()).hasSize(1);

        mockMvc.perform(comoServicio("/api/v1/correos/bienvenida")
                        .header("Idempotency-Key", "bienvenida-otra")
                        .content(cuerpo))
                .andExpect(status().isAccepted());
        mockMvc.perform(comoServicio("/api/v1/correos/bienvenida").content(cuerpo))
                .andExpect(status().isAccepted());
        assertThat(filas()).as("otra clave, o ninguna, si encola").hasSize(3);
    }

    @Test
    @DisplayName("un correo de misiones que el jugador no quiere queda OMITIDO y no sale")
    void unaMisionQueElJugadorNoQuiereQuedaOmitidaYNoSale() throws Exception {
        mockMvc.perform(comoServicio("/api/v1/correos/mision").content("""
                        {"email":"jugador@nexusbattles.test","apodo":"ElGuerrero",
                         "asunto":"Nueva misión","mensaje":"Derrota al dragón","debeEnviarCorreo":false}
                        """))
                .andExpect(status().isAccepted());

        Fila omitida = unicaFila();
        assertThat(omitida.estado()).isEqualTo("OMITIDO");
        assertThat(omitida.ultimoError()).contains("debeEnviarCorreo=false");
        assertThat(omitida.datos()).isEmpty();

        assertThat(trabajador(Clock.systemUTC()).procesarRonda()).isZero();
        assertThat(cuantosMensajes()).isZero();
    }

    // ------------------------------------------------------------------
    // Nada se pierde: reinicios, caidas y trabajadores concurrentes
    // ------------------------------------------------------------------

    @Test
    @DisplayName("tras un reinicio, un trabajador nuevo entrega todo lo que se acepto antes")
    void unReinicioNoPierdeLoAceptado() throws Exception {
        for (String quien : List.of("ana", "bea", "caro")) {
            mockMvc.perform(comoServicio("/api/v1/correos/bienvenida").content("""
                            {"email":"%s@nexusbattles.test","apodo":"%s"}
                            """.formatted(quien, quien)))
                    .andExpect(status().isAccepted());
        }

        // "Reiniciar" = el trabajador que habia se pierde con la memoria del
        // proceso; uno recien creado solo tiene la tabla.
        TrabajadorDeEntrega despuesDelReinicio = trabajador(Clock.systemUTC());

        assertThat(despuesDelReinicio.procesarRonda()).isEqualTo(3);
        assertThat(filas()).extracting(Fila::estado).containsOnly("DESVIADO");
        assertThat(cuantosMensajes()).isEqualTo(3);
    }

    @Test
    @DisplayName("un envio interrumpido a mitad (el servicio cayo) vuelve a la cola y sale una sola vez")
    void unEnvioInterrumpidoSeRecuperaYSaleUnaSolaVez() throws Exception {
        mockMvc.perform(comoServicio("/api/v1/correos/bienvenida").content("""
                        {"email":"interrumpido@nexusbattles.test","apodo":"Ana"}
                        """))
                .andExpect(status().isAccepted());
        RelojAjustable reloj = relojDespuesDeAceptar();
        // Un trabajador lo reclama y "muere" antes de anotar nada.
        assertThat(repositorio.reclamar(10, reloj.instant())).hasSize(1);
        assertThat(unicaFila().estado()).isEqualTo("ENVIANDO");

        TrabajadorDeEntrega otro = trabajador(reloj);
        reloj.adelantar(Duration.ofMinutes(2));
        assertThat(otro.procesarRonda()).as("a los dos minutos todavia puede estar enviandose").isZero();
        assertThat(unicaFila().estado()).isEqualTo("ENVIANDO");

        reloj.adelantar(Duration.ofMinutes(4));
        assertThat(otro.procesarRonda()).as("recuperado, pero con su espera de reintento").isZero();
        Fila recuperada = unicaFila();
        assertThat(recuperada.estado()).isEqualTo("ERROR_REINTENTABLE");
        assertThat(recuperada.ultimoError()).contains("interrumpida");
        assertThat(recuperada.proximoIntento()).isEqualTo(reloj.instant().plusSeconds(30));

        reloj.adelantar(Duration.ofSeconds(31));
        assertThat(otro.procesarRonda()).isEqualTo(1);
        Fila entregada = unicaFila();
        assertThat(entregada.estado()).isEqualTo("DESVIADO");
        assertThat(entregada.intentos()).as("el intento interrumpido cuenta").isEqualTo(2);
        assertThat(cuantosMensajes()).isEqualTo(1);
    }

    @Test
    @DisplayName("dos trabajadores a la vez sobre la misma cola no envian dos veces ningun correo")
    void dosTrabajadoresALaVezNoDuplicanNada() throws Exception {
        Instant antes = Instant.now().minusSeconds(1);
        int total = 20;
        for (int i = 0; i < total; i++) {
            repositorio.insertar(new NuevoEnvio(
                    UUID.randomUUID(), "bienvenida", "concurrente-" + i + "@nexusbattles.test",
                    "Bienvenido a The Nexus Battles VI", Map.of("saludo", "Jugador " + i),
                    EstadoDeEnvio.PENDIENTE, null, null, null, antes));
        }
        ConfiguracionDeEntrega loteCorto = new ConfiguracionDeEntrega(false, null, 3, null, null, null, null, null);
        List<TrabajadorDeEntrega> trabajadores =
                List.of(trabajador(Clock.systemUTC(), loteCorto), trabajador(Clock.systemUTC(), loteCorto));

        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(trabajadores.size());
        List<Future<Integer>> entregados = new ArrayList<>();
        for (TrabajadorDeEntrega trabajador : trabajadores) {
            Callable<Integer> vaciar = () -> {
                salida.await();
                int suyos = 0;
                for (int ronda = trabajador.procesarRonda(); ronda > 0; ronda = trabajador.procesarRonda()) {
                    suyos += ronda;
                }
                return suyos;
            };
            entregados.add(hilos.submit(vaciar));
        }
        salida.countDown();
        int suma = 0;
        for (Future<Integer> suyos : entregados) {
            suma += suyos.get(2, TimeUnit.MINUTES);
        }
        hilos.shutdown();

        assertThat(suma).as("entre los dos reclamaron cada fila una vez").isEqualTo(total);
        assertThat(filas()).hasSize(total)
                .allSatisfy(fila -> {
                    assertThat(fila.estado()).isEqualTo("DESVIADO");
                    assertThat(fila.intentos()).isEqualTo(1);
                });
        assertThat(cuantosMensajes()).as("ni uno de mas en la bandeja").isEqualTo(total);
    }

    @Test
    @DisplayName("con el SMTP caido el correo espera en ERROR_REINTENTABLE y sale cuando vuelve")
    void conElSmtpCaidoElCorreoEsperaYSaleCuandoVuelve() throws Exception {
        JavaMailSenderImpl principal = (JavaMailSenderImpl) mailSender;
        int puertoBueno = principal.getPort();
        int puertoCerrado;
        try (ServerSocket libre = new ServerSocket(0)) {
            puertoCerrado = libre.getLocalPort();
        }
        mockMvc.perform(comoServicio("/api/v1/correos/recuperacion-clave").content("""
                        {"email":"jugador@ejemplo.com","apodo":"ElGuerrero",
                         "codigo":"482915","minutosVigencia":30}
                        """))
                .andExpect(status().isAccepted());
        RelojAjustable reloj = relojDespuesDeAceptar();
        TrabajadorDeEntrega trabajador = trabajador(reloj);

        principal.setPort(puertoCerrado);
        try {
            assertThat(trabajador.procesarRonda()).isEqualTo(1);
            Fila fallida = unicaFila();
            assertThat(fallida.estado()).isEqualTo("ERROR_REINTENTABLE");
            assertThat(fallida.intentos()).isEqualTo(1);
            assertThat(fallida.ultimoError()).isNotBlank();
            assertThat(fallida.proximoIntento()).isEqualTo(reloj.instant().plusSeconds(30));
            assertThat(fallida.datos()).as("el reintento necesita el codigo").containsEntry("codigo", "482915");
            assertThat(trabajador.procesarRonda()).as("antes de su espera no se reintenta").isZero();
        } finally {
            principal.setPort(puertoBueno);
        }

        reloj.adelantar(Duration.ofSeconds(31));
        assertThat(trabajador.procesarRonda()).isEqualTo(1);

        Fila enviada = unicaFila();
        assertThat(enviada.estado()).as("direccion real: por el proveedor").isEqualTo("ENVIADO");
        assertThat(enviada.destino()).isEqualTo("PROVEEDOR");
        assertThat(enviada.intentos()).isEqualTo(2);
        assertThat(enviada.ultimoError()).isNull();
        assertThat(enviada.datos()).doesNotContainKey("codigo");
        Map<String, Object> mensaje = mensajePara("jugador@ejemplo.com");
        assertThat(mensaje.get("Subject")).isEqualTo("Recupera tu contraseña de The Nexus Battles VI");
        assertThat((String) mensaje.get("HTML"))
                .contains("482915")
                .contains("http://localhost/restablecer#codigo=482915&amp;correo=jugador%40ejemplo.com");
    }

    // ------------------------------------------------------------------
    // Plantillas nuevas de B1, de punta a punta
    // ------------------------------------------------------------------

    @Test
    @DisplayName("la sancion y la compra con detalle salen con su plantilla corporativa")
    void laSancionYLaCompraSalenConSuPlantilla() throws Exception {
        mockMvc.perform(comoServicio("/api/v1/correos/sancion").content("""
                        {"email":"sancionado@nexusbattles.test","apodo":"ElGuerrero","tipo":"SUSPENSION",
                         "motivo":"Acoso a otros jugadores","hasta":"2026-10-01T18:00:00-05:00",
                         "apelableHasta":"2026-10-25T23:59:00-05:00"}
                        """))
                .andExpect(status().isAccepted());
        mockMvc.perform(comoServicio("/api/v1/correos/confirmacion-compra").content("""
                        {"email":"comprador@nexusbattles.test","apodo":"ElGuerrero",
                         "monto":250.00,"moneda":"COP","concepto":"Compra en la tienda",
                         "fechaHora":"2026-09-23T10:15:00-05:00","orden":"ORD-2026-0042",
                         "lineas":[{"nombre":"Espada Legendaria","cantidad":2,"precioUnitario":100.00,"subtotal":200.00},
                                   {"nombre":"Poción","cantidad":1,"subtotal":50.00}]}
                        """))
                .andExpect(status().isAccepted());

        assertThat(trabajador(Clock.systemUTC()).procesarRonda()).isEqualTo(2);

        Map<String, Object> sancion = mensajePara("sancionado@nexusbattles.test");
        assertThat(sancion.get("Subject")).isEqualTo("Tu cuenta de The Nexus Battles VI está suspendida");
        assertThat((String) sancion.get("HTML"))
                .contains("THE NEXUS BATTLES VI")
                .contains("Acoso a otros jugadores")
                .contains("01/10/2026 a las 18:00 (GMT-05:00)")
                .contains("25/10/2026 a las 23:59 (GMT-05:00)");

        Map<String, Object> compra = mensajePara("comprador@nexusbattles.test");
        assertThat((String) compra.get("HTML"))
                .contains("Espada Legendaria").contains("200.00 COP")
                .contains("Poción").contains("ORD-2026-0042").contains("250.00 COP");
        assertThat((String) compra.get("Text"))
                .as("el detalle se lee tambien en texto plano, celda a celda")
                .contains("Espada Legendaria | 2 | 100.00 COP | 200.00 COP");
    }

    @Test
    @DisplayName("el correo sale con remitente, plantilla corporativa, logo incrustado y las dos versiones")
    void elCorreoSaleConLaPlantillaCorporativaElLogoYLasDosVersiones() throws Exception {
        ResultadoDeEntrega resultado = enviador.enviar(
                "rastreo@nexusbattles.test",
                "Asunto rastreable",
                "email/plantilla-prueba",
                Map.of("mensaje", "Texto que tiene que estar tambien en plano"));

        assertThat(resultado).isInstanceOf(ResultadoDeEntrega.Entregado.class);
        Map<String, Object> mensaje = mensajePara("rastreo@nexusbattles.test");
        @SuppressWarnings("unchecked")
        Map<String, Object> remitente = (Map<String, Object>) mensaje.get("From");
        assertThat(remitente.get("Address"))
                .as("el From es el configurado (MAIL_FROM), no el que decida el servidor")
                .isEqualTo("no-reply@nexusbattles.test");
        assertThat((String) mensaje.get("HTML")).contains("THE NEXUS BATTLES VI").contains("cid:logo-nexus");
        assertThat(mensaje.get("Attachments").toString()).as("el logo no va como adjunto suelto").isEqualTo("[]");
        assertThat(mensaje.get("Inline").toString()).contains("logo-nexus");
        assertThat((String) mensaje.get("Text")).contains("Texto que tiene que estar tambien en plano");
        assertThat(mailpit("/api/v1/message/" + mensaje.get("ID") + "/raw"))
                .contains("multipart/alternative")
                .contains("text/plain");
    }

    // ------------------------------------------------------------------
    // Evidencia de entrega, metricas y retencion
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GET /correos/envios lee la tabla, cuenta por estado y nunca ensena direcciones completas")
    void laEvidenciaDeEntregaLeeLaTablaYEnmascara() throws Exception {
        mockMvc.perform(comoServicio("/api/v1/correos/bienvenida").content("""
                        {"email":"victima@nexusbattles.test","apodo":"Victima"}
                        """))
                .andExpect(status().isAccepted());
        mockMvc.perform(comoServicio("/api/v1/correos/mision").content("""
                        {"email":"omitida@nexusbattles.test","apodo":"Ana","asunto":"a","mensaje":"m",
                         "debeEnviarCorreo":false}
                        """))
                .andExpect(status().isAccepted());
        trabajador(Clock.systemUTC()).procesarRonda();
        mockMvc.perform(comoServicio("/api/v1/correos/bienvenida").content("""
                        {"email":"pendiente@nexusbattles.test","apodo":"Bea"}
                        """))
                .andExpect(status().isAccepted());
        String administradora = "Bearer " + EMISOR.tokenDeUsuario("Admin", UUID.randomUUID(), "ADMINISTRADOR");

        String cuerpo = mockMvc.perform(get("/api/v1/correos/envios").header(HttpHeaders.AUTHORIZATION, administradora))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desviados").value(1))
                .andExpect(jsonPath("$.omitidos").value(1))
                .andExpect(jsonPath("$.pendientes").value(1))
                .andExpect(jsonPath("$.aceptados").value(0))
                .andExpect(jsonPath("$.rechazados").value(0))
                .andExpect(jsonPath("$.remitente").value("The Nexus Battles VI <no-reply@nexusbattles.test>"))
                .andExpect(jsonPath("$.recientes.length()").value(3))
                .andExpect(jsonPath("$.recientes[0].instante").isString())
                .andExpect(jsonPath("$.recientes[0].intentos").isNumber())
                .andReturn().getResponse().getContentAsString();
        assertThat(cuerpo)
                .contains("v***a@nexusbattles.test")
                .doesNotContain("victima@")
                .doesNotContain("pendiente@")
                .doesNotContain("omitida@");

        mockMvc.perform(get("/api/v1/correos/envios?estado=OMITIDO&ultimos=10")
                        .header(HttpHeaders.AUTHORIZATION, administradora))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recientes.length()").value(1))
                .andExpect(jsonPath("$.recientes[0].estado").value("OMITIDO"))
                .andExpect(jsonPath("$.recientes[0].plantilla").value("mision"))
                .andExpect(jsonPath("$.recientes[0].intentos").value(0));
        mockMvc.perform(get("/api/v1/correos/envios?ultimos=1").header(HttpHeaders.AUTHORIZATION, administradora))
                .andExpect(jsonPath("$.recientes.length()").value(1));
        mockMvc.perform(get("/api/v1/correos/envios?estado=INVENTADO").header(HttpHeaders.AUTHORIZATION, administradora))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/correos/envios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + EMISOR.tokenDeServicio("metricas-plataforma")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/correos/envios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + EMISOR.tokenDeJugador("Ana", UUID.randomUUID())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/correos/envios")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("las metricas cuentan cada estado por plantilla y los pendientes salen de la tabla")
    void lasMetricasCuentanLoQuePasaEnLaCola() throws Exception {
        double pendientesAntes = contador("PENDIENTE", "bienvenida");
        double desviadosAntes = contador("DESVIADO", "bienvenida");

        mockMvc.perform(comoServicio("/api/v1/correos/bienvenida").content("""
                        {"email":"medida@nexusbattles.test","apodo":"Ana"}
                        """))
                .andExpect(status().isAccepted());
        assertThat(medidor.get("correo.pendientes").gauge().value()).isEqualTo(1.0);
        assertThat(contador("PENDIENTE", "bienvenida")).isEqualTo(pendientesAntes + 1);

        trabajador(Clock.systemUTC()).procesarRonda();

        assertThat(contador("DESVIADO", "bienvenida")).isEqualTo(desviadosAntes + 1);
        assertThat(medidor.get("correo.pendientes").gauge().value()).isZero();
    }

    @Test
    @DisplayName("la purga borra lo terminado hace mas de treinta dias y nada de lo que espera")
    void laPurgaBorraSoloLoTerminadoYViejo() {
        Instant ahora = Instant.now();
        Instant haceCuarentaDias = ahora.minus(Duration.ofDays(40));
        insertar("viejo-terminado@nexusbattles.test", EstadoDeEnvio.OMITIDO, haceCuarentaDias);
        insertar("viejo-pendiente@nexusbattles.test", EstadoDeEnvio.PENDIENTE, haceCuarentaDias);
        insertar("reciente-terminado@nexusbattles.test", EstadoDeEnvio.OMITIDO, ahora.minus(Duration.ofDays(1)));

        assertThat(trabajador(Clock.systemUTC()).purgar()).isEqualTo(1);

        assertThat(jdbc.sql("SELECT destinatario FROM correo.envios ORDER BY destinatario")
                        .query(String.class).list())
                .containsExactly("reciente-terminado@nexusbattles.test", "viejo-pendiente@nexusbattles.test");
    }

    // ------------------------------------------------------------------
    // Apoyo
    // ------------------------------------------------------------------

    private static MockHttpServletRequestBuilder comoServicio(String ruta) {
        return post(ruta)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + EMISOR.tokenDeServicio("ms-identidad"))
                .contentType(MediaType.APPLICATION_JSON);
    }

    private TrabajadorDeEntrega trabajador(Clock reloj) {
        return trabajador(reloj, ConfiguracionDeEntrega.porOmision());
    }

    /**
     * Un reloj un segundo por delante de lo recien aceptado (que se guardo con
     * el reloj del sistema), en milisegundos exactos para poder comparar con
     * lo que devuelve la base.
     */
    private static RelojAjustable relojDespuesDeAceptar() {
        return new RelojAjustable(Instant.now().plusSeconds(1).truncatedTo(ChronoUnit.MILLIS));
    }

    private TrabajadorDeEntrega trabajador(Clock reloj, ConfiguracionDeEntrega configuracion) {
        return new TrabajadorDeEntrega(
                repositorio,
                enviador,
                composicion,
                new MaquinaDeEstados(new PoliticaDeReintentos(configuracion.esperas(), configuracion.maxIntentos())),
                configuracion,
                metricas,
                new TransactionTemplate(transacciones),
                reloj);
    }

    private void insertar(String destinatario, EstadoDeEnvio estado, Instant creadoEn) {
        repositorio.insertar(new NuevoEnvio(UUID.randomUUID(), "bienvenida", destinatario, "Asunto",
                Map.of(), estado, estado == EstadoDeEnvio.OMITIDO ? "prueba" : null, null, null, creadoEn));
    }

    private double contador(String estado, String plantilla) {
        Counter contador = medidor.find("correo.envios").tags("estado", estado, "plantilla", plantilla).counter();
        return contador == null ? 0 : contador.count();
    }

    /** Una fila de la cola, leida sin pasar por el codigo que se prueba. */
    record Fila(
            String estado,
            int intentos,
            Map<String, Object> datos,
            String destino,
            String identificador,
            String ultimoError,
            String trazaId,
            Instant proximoIntento,
            Instant enviadoEn) {
    }

    private List<Fila> filas() {
        return jdbc.sql("""
                        SELECT estado, intentos, datos::text AS datos, destino, identificador, ultimo_error,
                               trace_id, proximo_intento, enviado_en
                          FROM correo.envios ORDER BY creado_en, destinatario
                        """)
                .query((fila, n) -> new Fila(
                        fila.getString("estado"),
                        fila.getInt("intentos"),
                        json(fila.getString("datos")),
                        fila.getString("destino"),
                        fila.getString("identificador"),
                        fila.getString("ultimo_error"),
                        fila.getString("trace_id"),
                        instante(fila.getObject("proximo_intento", OffsetDateTime.class)),
                        instante(fila.getObject("enviado_en", OffsetDateTime.class))))
                .list();
    }

    private Fila unicaFila() {
        List<Fila> filas = filas();
        assertThat(filas).hasSize(1);
        return filas.get(0);
    }

    private static Instant instante(OffsetDateTime valor) {
        return valor == null ? null : valor.toInstant();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> json(String texto) {
        return JSON.readValue(texto, Map.class);
    }

    private static String baseMailpit() {
        return "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025);
    }

    private static String mailpit(String ruta) throws Exception {
        HttpResponse<String> respuesta = HTTP.send(
                HttpRequest.newBuilder(URI.create(baseMailpit() + ruta)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(respuesta.statusCode()).isEqualTo(200);
        return respuesta.body();
    }

    private static int cuantosMensajes() throws Exception {
        Map<String, Object> bandeja = json(mailpit("/api/v1/messages"));
        Object cuantos = bandeja.getOrDefault("messages_count", bandeja.get("total"));
        return ((Number) cuantos).intValue();
    }

    /** El unico mensaje para esa direccion, con su HTML y su texto. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> mensajePara(String direccion) throws Exception {
        Map<String, Object> busqueda = json(mailpit("/api/v1/search?query="
                + URLEncoder.encode("to:" + direccion, StandardCharsets.UTF_8)));
        List<Map<String, Object>> mensajes = (List<Map<String, Object>>) busqueda.get("messages");
        assertThat(mensajes).as("mensajes para %s", direccion).hasSize(1);
        return json(mailpit("/api/v1/message/" + mensajes.get(0).get("ID")));
    }
}
