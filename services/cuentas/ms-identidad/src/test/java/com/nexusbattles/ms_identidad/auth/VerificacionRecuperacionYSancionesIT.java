package com.nexusbattles.ms_identidad.auth;

import com.nexusbattles.ms_identidad.onboarding.ServidorFalso;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BACKEND-02 de punta a punta con PostgreSQL de verdad (Flyway V1..V3 y
 * Hibernate en validate), Tomcat, los interceptores y HTTP real hacia
 * servidores falsos de correo, moderacion-sanciones y auditoria.
 *
 * <p>Lo que demuestra y ninguna prueba unitaria puede: que el codigo que
 * llega al correo es el que la base sabe comprobar (y que en la base no esta
 * en claro), que dos confirmaciones simultaneas dejan UNA alta, que los
 * intentos se cuentan con la fila bloqueada, que la recuperacion cierra las
 * sesiones, y que la proyeccion de una sancion y la delegacion del panel
 * cambian de verdad lo que el login responde.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=true",
        "spring.flyway.baseline-version=1",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "app.onboarding.ejecucion=manual",
        "app.onboarding.reintentos-automaticos=false",
        "app.seguridad.permitir-header-rol=false",
        "app.seguridad.umbral-intentos-fallidos=5",
        "identidad.correo.envio=sincrono",
        "identidad.verificacion.segundos-entre-reenvios=0",
        "identidad.verificacion.reenvios-por-hora=3",
        "identidad.recuperacion.segundos-entre-solicitudes=0",
        "app.servicios.clientes=moderacion-sanciones=secreto-de-moderacion-para-pruebas;"
                + "ms-ecommerce=secreto-de-ecommerce-para-pruebas"
})
@ActiveProfiles("test")
@Testcontainers
@DisplayName("Correo verificado, recuperacion endurecida y sanciones unificadas (BACKEND-02), con PostgreSQL")
class VerificacionRecuperacionYSancionesIT {

    private static final String CLAVE = "Segura-123!";
    private static final String TIPOS = "https://nexusbattles.upb.edu.co/errors/";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> BASE = new PostgreSQLContainer<>("postgres:15-alpine");

    static final ServidorFalso PLATAFORMA = new ServidorFalso();
    static final ServidorFalso MODERACION = new ServidorFalso();

    @DynamicPropertySource
    static void servicios(DynamicPropertyRegistry registro) {
        String correos = PLATAFORMA.url() + "/api/v1/correos/";
        registro.add("app.correo.url-bienvenida", () -> correos + "bienvenida");
        registro.add("app.correo.url-aviso-acceso", () -> correos + "aviso-acceso");
        registro.add("app.correo.url-confirmacion-cuenta", () -> correos + "confirmacion-cuenta");
        registro.add("app.correo.url-recuperacion-clave", () -> correos + "recuperacion-clave");
        registro.add("app.correo.url-cambio-clave", () -> correos + "cambio-clave");
        registro.add("app.notificaciones.url", () -> PLATAFORMA.url() + "/api/v1/internal/notifications");
        registro.add("app.auditoria.url", () -> PLATAFORMA.url() + "/api/v1/admin/auditoria/eventos");
        registro.add("app.lista-negra.url", () -> MODERACION.url() + "/api/v1/lista-negra/verificar");
        registro.add("app.sanciones.url", () -> MODERACION.url() + "/api/v1");
    }

    @LocalServerPort
    private int puerto;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository usuarios;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager gestorDeTransacciones;

    private org.springframework.transaction.support.TransactionTemplate transacciones;

    @BeforeEach
    void prepararTransacciones() {
        transacciones = new org.springframework.transaction.support.TransactionTemplate(gestorDeTransacciones);
    }

    @AfterAll
    static void apagar() {
        PLATAFORMA.close();
        MODERACION.close();
    }

    @BeforeEach
    void serviciosSanos() {
        PLATAFORMA.responder("POST", "/api/v1/correos/.*", 202, "")
                .responder("POST", "/api/v1/internal/notifications", 202, "{}")
                .responder("POST", "/api/v1/admin/auditoria/eventos", 201, "{}");
        MODERACION.responder("POST", "/api/v1/lista-negra/verificar", 200, "{\"aprobado\":true,\"accion\":\"PERMITIR\"}");
    }

    // ------------------------------------------------------------ ayudantes

    record Respuesta(int estado, String cuerpo, HttpHeaders cabeceras) {
    }

    private RestClient cliente() {
        return RestClient.create("http://localhost:" + puerto);
    }

    private Respuesta enviar(RestClient.RequestHeadersSpec<?> peticion) {
        return peticion.exchange((p, r) -> new Respuesta(r.getStatusCode().value(),
                new String(r.getBody().readAllBytes(), StandardCharsets.UTF_8), r.getHeaders()));
    }

    private Respuesta json(String metodo, String ruta, String cuerpo, String token) {
        RestClient.RequestBodySpec peticion = cliente().method(org.springframework.http.HttpMethod.valueOf(metodo))
                .uri(ruta).contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT, "application/json, application/problem+json");
        if (token != null) {
            peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return enviar(cuerpo == null ? peticion : peticion.body(cuerpo));
    }

    private Respuesta registrar(String apodo) {
        MultiValueMap<String, Object> formulario = new LinkedMultiValueMap<>();
        formulario.add("nombres", "Nombre");
        formulario.add("apellidos", "Apellido");
        formulario.add("email", apodo + "@upb.edu.co");
        formulario.add("password", CLAVE);
        formulario.add("apodo", apodo);
        return enviar(cliente().post().uri("/api/v1/auth/registro").contentType(MediaType.MULTIPART_FORM_DATA)
                .header(HttpHeaders.ACCEPT, "application/json, application/problem+json").body(formulario));
    }

    private Respuesta login(String correo, String clave) {
        return json("POST", "/api/v1/auth/login",
                "{\"email\":\"" + correo + "\",\"password\":\"" + clave + "\"}", null);
    }

    private static List<ServidorFalso.Peticion> correosA(String plantilla, String correo) {
        return PLATAFORMA.recibidas("POST", "/api/v1/correos/" + plantilla).stream()
                .filter(p -> p.cuerpo().contains("\"email\":\"" + correo + "\"")).toList();
    }

    private static String ultimoCodigo(String plantilla, String correo) {
        List<ServidorFalso.Peticion> enviados = correosA(plantilla, correo);
        assertThat(enviados).as(plantilla + " para " + correo).isNotEmpty();
        return campo(enviados.get(enviados.size() - 1).cuerpo(), "codigo");
    }

    private static String campo(String json, String nombre) {
        Matcher m = Pattern.compile("\"" + nombre + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private Respuesta confirmar(String correo, String codigo) {
        return json("POST", "/api/v1/auth/verificacion/confirmacion",
                "{\"email\":\"" + correo + "\",\"codigo\":\"" + codigo + "\"}", null);
    }

    /** Registra, confirma el correo y entra: la cuenta de un jugador normal. */
    private String jugadorActivo(String apodo) {
        assertThat(registrar(apodo).estado()).isEqualTo(201);
        String correo = apodo + "@upb.edu.co";
        assertThat(confirmar(correo, ultimoCodigo("confirmacion-cuenta", correo)).estado()).isEqualTo(200);
        Respuesta sesion = login(correo, CLAVE);
        assertThat(sesion.estado()).as(sesion.cuerpo()).isEqualTo(200);
        return campo(sesion.cuerpo(), "token");
    }

    /** Espera hasta 5 s a que algo que ocurre en segundo plano se vea. */
    private static boolean llega(java.util.function.BooleanSupplier condicion) {
        long limite = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < limite) {
            if (condicion.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return condicion.getAsBoolean();
    }

    private UUID uidDe(String apodo) {
        return jdbc.queryForObject("SELECT public_id FROM usuarios WHERE apodo = ?", UUID.class, apodo);
    }

    private String tokenDeServicio(String cliente, String secreto) {
        Respuesta token = enviar(cliente().post().uri("/api/v1/auth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=client_credentials&client_id=" + cliente + "&client_secret=" + secreto));
        assertThat(token.estado()).as(token.cuerpo()).isEqualTo(200);
        return campo(token.cuerpo(), "access_token");
    }

    // ----------------------------------------------------------- verificacion

    @Test
    @DisplayName("registro: cuenta PENDIENTE, codigo al correo (VERIFICACION, con clave) y en la base solo su resumen")
    void registroPendiente() {
        Respuesta alta = registrar("ana");
        assertThat(alta.estado()).as(alta.cuerpo()).isEqualTo(201);
        assertThat(alta.cuerpo()).contains("\"estado\":\"PENDIENTE_VERIFICACION\"").doesNotContain("password");

        ServidorFalso.Peticion correo = correosA("confirmacion-cuenta", "ana@upb.edu.co").get(0);
        String codigo = campo(correo.cuerpo(), "codigo");
        assertThat(codigo).matches("[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{8}");
        assertThat(correo.cuerpo()).contains("\"proposito\":\"VERIFICACION\"").contains("\"minutosVigencia\":1440");
        assertThat(correo.cabecera("Idempotency-Key")).matches("verificacion-\\d+");
        assertThat(correo.cabecera("Authorization")).as("credencial de servicio de ms-identidad").startsWith("Bearer ");
        assertThat(correosA("bienvenida", "ana@upb.edu.co")).as("la bienvenida espera a la verificacion").isEmpty();

        Map<String, Object> fila = jdbc.queryForMap("SELECT t.token, t.codigo_hash FROM tokens_credencial t"
                + " JOIN usuarios u ON u.id = t.usuario_id WHERE u.apodo = 'ana'");
        assertThat(fila.get("token")).isNull();
        assertThat((String) fila.get("codigo_hash")).startsWith("$2").doesNotContain(codigo);
        assertThat(new BCryptPasswordEncoder().matches(codigo, (String) fila.get("codigo_hash"))).isTrue();

        Respuesta sinVerificar = login("ana@upb.edu.co", CLAVE);
        assertThat(sinVerificar.estado()).isEqualTo(403);
        assertThat(sinVerificar.cuerpo()).contains(TIPOS + "cuenta-no-verificada");
        assertThat(login("ana@upb.edu.co", "Otra.Clave-9").estado()).as("sin la contrasena, 401 y nada mas")
                .isEqualTo(401);
    }

    @Test
    @DisplayName("confirmar: la cuenta pasa a ACTIVO, empieza el alta del jugador (una vez), bienvenida y entra")
    void confirmacion() {
        registrar("beto");
        String codigo = ultimoCodigo("confirmacion-cuenta", "beto@upb.edu.co");

        Respuesta confirmada = confirmar("beto@upb.edu.co", " " + codigo.toLowerCase() + " ");
        assertThat(confirmada.estado()).as(confirmada.cuerpo()).isEqualTo(200);
        assertThat(confirmada.cuerpo()).contains("\"estado\":\"ACTIVO\"");
        assertThat(jdbc.queryForObject("SELECT estado FROM usuarios WHERE apodo = 'beto'", String.class))
                .isEqualTo("ACTIVO");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM onboarding_jugador WHERE usuario_uid = ?",
                Integer.class, uidDe("beto"))).isEqualTo(1);
        assertThat(correosA("bienvenida", "beto@upb.edu.co")).hasSize(1)
                .allSatisfy(p -> assertThat(p.cabecera("Idempotency-Key")).isEqualTo("bienvenida-" + uidDe("beto")));
        // La auditoria de la cuenta sale en segundo plano (fail-open): se espera a que llegue.
        assertThat(llega(() -> PLATAFORMA.recibidas("POST", "/api/v1/admin/auditoria/eventos").stream()
                .anyMatch(p -> p.cuerpo().contains("CORREO_VERIFICADO")))).isTrue();

        Respuesta otraVez = confirmar("beto@upb.edu.co", codigo);
        assertThat(otraVez.estado()).as("un codigo ya usado").isEqualTo(400);
        assertThat(otraVez.cuerpo()).contains(TIPOS + "codigo-invalido");
        assertThat(login("beto@upb.edu.co", CLAVE).estado()).isEqualTo(200);
    }

    @Test
    @DisplayName("dos confirmaciones simultaneas con el codigo correcto: una gana, la otra 400, y UNA sola alta")
    void confirmacionesSimultaneas() throws Exception {
        registrar("cata");
        String codigo = ultimoCodigo("confirmacion-cuenta", "cata@upb.edu.co");
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> resultados = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                resultados.add(hilos.submit(() -> {
                    salida.await();
                    return confirmar("cata@upb.edu.co", codigo).estado();
                }));
            }
            salida.countDown();
            List<Integer> estados = new ArrayList<>();
            for (Future<Integer> r : resultados) {
                estados.add(r.get());
            }
            assertThat(estados).containsExactlyInAnyOrder(200, 400);
        } finally {
            hilos.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM onboarding_jugador WHERE usuario_uid = ?",
                Integer.class, uidDe("cata"))).isEqualTo(1);
        assertThat(correosA("bienvenida", "cata@upb.edu.co")).hasSize(1);
    }

    @Test
    @DisplayName("cinco fallos anulan el codigo (429), ni el correcto sirve despues; un correo inexistente responde igual")
    void intentos() {
        registrar("dani");
        String codigo = ultimoCodigo("confirmacion-cuenta", "dani@upb.edu.co");
        for (int i = 0; i < 4; i++) {
            Respuesta fallo = confirmar("dani@upb.edu.co", "ZZZZ2222");
            assertThat(fallo.estado()).isEqualTo(400);
            assertThat(fallo.cuerpo()).contains(TIPOS + "codigo-invalido");
        }
        Respuesta quinto = confirmar("dani@upb.edu.co", "ZZZZ2222");
        assertThat(quinto.estado()).isEqualTo(429);
        assertThat(quinto.cuerpo()).contains(TIPOS + "demasiados-intentos");
        assertThat(confirmar("dani@upb.edu.co", codigo).estado()).isEqualTo(429);
        assertThat(jdbc.queryForObject("SELECT estado FROM usuarios WHERE apodo = 'dani'", String.class))
                .isEqualTo("PENDIENTE_VERIFICACION");

        Respuesta nadie = confirmar("nadie@upb.edu.co", codigo);
        assertThat(nadie.estado()).isEqualTo(400);
        assertThat(nadie.cuerpo()).contains(TIPOS + "codigo-invalido");

        // Un codigo nuevo (reenvio) vuelve a dejar confirmar.
        assertThat(json("POST", "/api/v1/auth/verificacion/reenvio", "{\"email\":\"dani@upb.edu.co\"}", null)
                .estado()).isEqualTo(202);
        assertThat(confirmar("dani@upb.edu.co", ultimoCodigo("confirmacion-cuenta", "dani@upb.edu.co")).estado())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("reenvio: siempre 202 y el mismo texto; anula el anterior; pasado el limite por hora no envia nada")
    void reenvio() {
        registrar("eva");
        String primero = ultimoCodigo("confirmacion-cuenta", "eva@upb.edu.co");

        Respuesta neutro = json("POST", "/api/v1/auth/verificacion/reenvio", "{\"email\":\"eva@upb.edu.co\"}", null);
        Respuesta sinCuenta = json("POST", "/api/v1/auth/verificacion/reenvio", "{\"email\":\"nadie@upb.edu.co\"}", null);
        assertThat(neutro.estado()).isEqualTo(202);
        assertThat(sinCuenta.estado()).isEqualTo(202);
        assertThat(sinCuenta.cuerpo()).isEqualTo(neutro.cuerpo());

        assertThat(confirmar("eva@upb.edu.co", primero).estado()).as("el anterior quedo anulado").isEqualTo(400);

        json("POST", "/api/v1/auth/verificacion/reenvio", "{\"email\":\"eva@upb.edu.co\"}", null);
        json("POST", "/api/v1/auth/verificacion/reenvio", "{\"email\":\"eva@upb.edu.co\"}", null);
        assertThat(correosA("confirmacion-cuenta", "eva@upb.edu.co")).as("registro + 2 reenvios = limite de 3")
                .hasSize(3);
    }

    // ------------------------------------------------------------ recuperacion

    @Test
    @DisplayName("recuperacion con preguntas: respuesta neutra, preguntas a cambio del codigo, respuestas, sesiones cerradas")
    void recuperacionConPreguntas() {
        String token = jugadorActivo("fede");
        Respuesta configuradas = json("PUT", "/api/v1/auth/preguntas-seguridad", "{\"passwordActual\":\"" + CLAVE + "\","
                + "\"preguntas\":[{\"texto\":\"¿Ciudad donde naciste?\",\"respuesta\":\"Bogotá\"},"
                + "{\"texto\":\"¿Primera mascota?\",\"respuesta\":\"Firulais\"}]}", token);
        assertThat(configuradas.estado()).as(configuradas.cuerpo()).isEqualTo(200);
        assertThat(configuradas.cuerpo()).doesNotContain("Bogot").doesNotContain("Firulais");

        Respuesta existe = json("POST", "/api/v1/auth/restablecer/solicitar", "{\"email\":\"fede@upb.edu.co\"}", null);
        Respuesta noExiste = json("POST", "/api/v1/auth/restablecer/solicitar", "{\"email\":\"nadie@upb.edu.co\"}", null);
        assertThat(existe.estado()).isEqualTo(200);
        assertThat(noExiste.estado()).isEqualTo(200);
        assertThat(existe.cuerpo()).isEqualTo(noExiste.cuerpo())
                .isEqualTo("Si existe una cuenta asociada, recibirás instrucciones.");
        assertThat(correosA("recuperacion-clave", "nadie@upb.edu.co")).isEmpty();
        String codigo = ultimoCodigo("recuperacion-clave", "fede@upb.edu.co");

        Respuesta preguntas = json("POST", "/api/v1/auth/restablecer/preguntas",
                "{\"email\":\"fede@upb.edu.co\",\"codigo\":\"" + codigo + "\"}", null);
        assertThat(preguntas.estado()).isEqualTo(200);
        assertThat(preguntas.cuerpo()).contains("\"configuradas\":true").contains("¿Ciudad donde naciste?");
        Matcher ids = Pattern.compile("\"id\":\"([0-9a-f-]{36})\"").matcher(preguntas.cuerpo());
        List<String> preguntaIds = new ArrayList<>();
        while (ids.find()) {
            preguntaIds.add(ids.group(1));
        }
        assertThat(preguntaIds).hasSize(2);

        String sinRespuestas = "{\"email\":\"fede@upb.edu.co\",\"codigo\":\"" + codigo + "\",\"nuevaPassword\":\"Nueva.Clave-9\"}";
        Respuesta faltan = json("POST", "/api/v1/auth/restablecer/confirmar", sinRespuestas, null);
        assertThat(faltan.estado()).isEqualTo(422);
        assertThat(faltan.cuerpo()).contains(TIPOS + "respuestas-incorrectas");

        String conRespuestas = "{\"email\":\"fede@upb.edu.co\",\"codigo\":\"" + codigo + "\",\"nuevaPassword\":\"Nueva.Clave-9\","
                + "\"respuestas\":[{\"preguntaId\":\"" + preguntaIds.get(0) + "\",\"respuesta\":\"  BOGOTA \"},"
                + "{\"preguntaId\":\"" + preguntaIds.get(1) + "\",\"respuesta\":\"firulais\"}]}";
        Respuesta politica = json("POST", "/api/v1/auth/restablecer/confirmar",
                conRespuestas.replace("Nueva.Clave-9", "debilita1"), null);
        assertThat(politica.estado()).isEqualTo(422);
        assertThat(politica.cuerpo()).contains(TIPOS + "contrasena-no-cumple-politica");

        Respuesta canje = json("POST", "/api/v1/auth/restablecer/confirmar", conRespuestas, null);
        assertThat(canje.estado()).as(canje.cuerpo()).isEqualTo(200);
        assertThat(canje.cuerpo()).contains("Contraseña actualizada");

        assertThat(json("GET", "/api/v1/auth/preguntas-seguridad", null, token).estado())
                .as("la sesion abierta se cerro (versionToken++)").isEqualTo(403);
        assertThat(login("fede@upb.edu.co", CLAVE).estado()).isEqualTo(401);
        assertThat(login("fede@upb.edu.co", "Nueva.Clave-9").estado()).isEqualTo(200);
        assertThat(correosA("cambio-clave", "fede@upb.edu.co")).hasSize(1);
        assertThat(json("POST", "/api/v1/auth/restablecer/confirmar", conRespuestas, null).estado())
                .as("un solo uso").isEqualTo(400);
    }

    @Test
    @DisplayName("una cuenta pendiente de verificar no recibe codigo de restablecimiento")
    void pendienteNoRecupera() {
        registrar("gael");
        json("POST", "/api/v1/auth/restablecer/solicitar", "{\"email\":\"gael@upb.edu.co\"}", null);
        assertThat(correosA("recuperacion-clave", "gael@upb.edu.co")).isEmpty();
    }

    // -------------------------------------------------------------- lista negra

    @Test
    @DisplayName("lista negra: rechazo -> 400 apodo-no-permitido; caida -> 503 con Retry-After y ninguna cuenta")
    void listaNegra() {
        MODERACION.responder("POST", "/api/v1/lista-negra/verificar", 200,
                "{\"aprobado\":false,\"accion\":\"RECHAZAR\",\"motivo\":\"El apodo no está permitido.\"}");
        Respuesta rechazado = registrar("spiderman");
        assertThat(rechazado.estado()).isEqualTo(400);
        assertThat(rechazado.cuerpo()).contains(TIPOS + "apodo-no-permitido");
        assertThat(MODERACION.recibidas("POST", "/api/v1/lista-negra/verificar").stream()
                .map(ServidorFalso.Peticion::cuerpo)).anyMatch(c -> c.contains("\"contexto\":\"APODO\""));

        MODERACION.responder("POST", "/api/v1/lista-negra/verificar", 500, "{}");
        Respuesta caida = registrar("hugo");
        assertThat(caida.estado()).isEqualTo(503);
        assertThat(caida.cabeceras().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("30");
        assertThat(caida.cuerpo()).contains(TIPOS + "moderacion-no-disponible");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuarios WHERE apodo IN ('spiderman','hugo')",
                Integer.class)).isZero();
    }

    // ---------------------------------------------------------------- sanciones

    @Test
    @DisplayName("proyeccion interna: solo moderacion; SUSPENDIDO niega el login con su fin, idempotente; ACTIVO levanta")
    void proyeccionInterna() {
        String sesion = jugadorActivo("ines");
        UUID uid = uidDe("ines");
        UUID sancion = UUID.randomUUID();
        String moderacion = tokenDeServicio("moderacion-sanciones", "secreto-de-moderacion-para-pruebas");
        String ecommerce = tokenDeServicio("ms-ecommerce", "secreto-de-ecommerce-para-pruebas");
        String cuerpo = "{\"estado\":\"SUSPENDIDO\",\"hasta\":\"2030-01-01T00:00:00Z\",\"sancionId\":\"" + sancion + "\"}";

        assertThat(json("PUT", "/api/v1/internal/usuarios/" + uid + "/estado-sancion", cuerpo, ecommerce).estado())
                .as("otro servicio no fija sanciones").isEqualTo(403);
        assertThat(json("PUT", "/api/v1/internal/usuarios/" + uid + "/estado-sancion", cuerpo, sesion).estado())
                .as("un jugador tampoco").isEqualTo(403);

        Respuesta proyectada = json("PUT", "/api/v1/internal/usuarios/" + uid + "/estado-sancion", cuerpo, moderacion);
        assertThat(proyectada.estado()).as(proyectada.cuerpo()).isEqualTo(200);
        assertThat(proyectada.cuerpo()).contains("\"estado\":\"SUSPENDIDO\"");
        Integer version = jdbc.queryForObject("SELECT version_token FROM usuarios WHERE apodo = 'ines'", Integer.class);
        json("PUT", "/api/v1/internal/usuarios/" + uid + "/estado-sancion", cuerpo, moderacion);
        assertThat(jdbc.queryForObject("SELECT version_token FROM usuarios WHERE apodo = 'ines'", Integer.class))
                .as("idempotente").isEqualTo(version);

        assertThat(json("GET", "/api/v1/auth/preguntas-seguridad", null, sesion).estado())
                .as("la sesion abierta quedo revocada").isEqualTo(403);
        Respuesta negado = login("ines@upb.edu.co", CLAVE);
        assertThat(negado.estado()).isEqualTo(403);
        assertThat(negado.cuerpo()).contains(TIPOS + "cuenta-suspendida").contains("\"suspendidoHasta\":\"2030-01-01T00:00:00Z\"");

        Respuesta contacto = json("GET", "/api/v1/internal/usuarios/" + uid + "/contacto", null, ecommerce);
        assertThat(contacto.estado()).isEqualTo(200);
        assertThat(contacto.cuerpo()).contains("\"email\":\"ines@upb.edu.co\"").contains("\"estado\":\"SUSPENDIDO\"");

        json("PUT", "/api/v1/internal/usuarios/" + uid + "/estado-sancion",
                "{\"estado\":\"ACTIVO\",\"sancionId\":\"" + sancion + "\"}", moderacion);
        assertThat(login("ines@upb.edu.co", CLAVE).estado()).isEqualTo(200);
    }

    @Test
    @DisplayName("un guardado de la cuenta (p. ej. el login) no pisa una sancion proyectada mientras tanto")
    void guardarNoPisaLaSancion() {
        jugadorActivo("mara");
        Long id = jdbc.queryForObject("SELECT id FROM usuarios WHERE apodo = 'mara'", Long.class);

        transacciones.executeWithoutResult(estado -> {
            var cuenta = usuarios.findById(id).orElseThrow();
            // Mientras esta transaccion tiene la cuenta leida, llega la sancion
            // por otra conexion (la ruta interna, en otro hilo).
            jdbc.update("UPDATE usuarios SET estado = 'SUSPENDIDO' WHERE id = ?", id);
            cuenta.setUltimoAcceso(java.time.LocalDateTime.now());
            usuarios.save(cuenta);
        });

        assertThat(jdbc.queryForObject("SELECT estado FROM usuarios WHERE id = ?", String.class, id))
                .as("@DynamicUpdate: el UPDATE solo lleva las columnas que cambiaron").isEqualTo("SUSPENDIDO");
    }

    @Test
    @DisplayName("panel: suspender delega en moderacion con el token del administrador; si moderacion cae, 503 y nada cambia")
    void panelDelega() {
        String admin = jugadorActivo("jefa");
        jdbc.update("UPDATE usuarios SET rol_id = (SELECT id FROM roles WHERE nombre = 'ADMINISTRADOR'), version_token = 0"
                + " WHERE apodo = 'jefa'");
        admin = campo(login("jefa@upb.edu.co", CLAVE).cuerpo(), "token");
        jugadorActivo("kiko");
        Long kiko = jdbc.queryForObject("SELECT id FROM usuarios WHERE apodo = 'kiko'", Long.class);
        UUID sancion = UUID.randomUUID();
        MODERACION.responder("POST", "/api/v1/sanciones", 201, "{\"id\":\"" + sancion + "\",\"tipo\":\"SUSPENSION\","
                + "\"vigenteHasta\":\"2030-01-01T00:00:00Z\",\"vigente\":true}");

        Respuesta suspendida = json("PUT", "/api/v1/admin/usuarios/" + kiko + "/suspender",
                "{\"suspendidoHasta\":\"2029-12-31T20:00:00\",\"motivo\":\"Spam en el chat\"}", admin);
        assertThat(suspendida.estado()).as(suspendida.cuerpo()).isEqualTo(204);
        ServidorFalso.Peticion emitida = MODERACION.recibidas("POST", "/api/v1/sanciones").get(0);
        assertThat(emitida.cabecera("Authorization")).isEqualTo("Bearer " + admin);
        assertThat(emitida.cuerpo()).contains("\"tipo\":\"SUSPENSION\"").contains("\"motivo\":\"Spam en el chat\"");
        assertThat(jdbc.queryForMap("SELECT estado, sancion_id FROM usuarios WHERE id = ?", kiko))
                .containsEntry("estado", "SUSPENDIDO").containsEntry("sancion_id", sancion);

        MODERACION.responder("POST", "/api/v1/sanciones", 503, "{}");
        jugadorActivo("lola");
        Long lola = jdbc.queryForObject("SELECT id FROM usuarios WHERE apodo = 'lola'", Long.class);
        Respuesta caida = json("PUT", "/api/v1/admin/usuarios/" + lola + "/banear", null, admin);
        assertThat(caida.estado()).isEqualTo(503);
        assertThat(caida.cuerpo()).contains(TIPOS + "moderacion-no-disponible");
        assertThat(jdbc.queryForObject("SELECT estado FROM usuarios WHERE id = ?", String.class, lola))
                .as("nunca divergir en silencio").isEqualTo("ACTIVO");
    }
}
