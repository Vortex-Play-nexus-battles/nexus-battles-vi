package com.nexusbattles.ms_identidad.auth.segundofactor;

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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HU-AUT-007 de punta a punta con PostgreSQL de verdad (Flyway V1..V5 y
 * Hibernate en validate), Tomcat y los interceptores.
 *
 * <p>Lo que solo se ve aqui: que el secreto y los codigos no estan en claro en
 * la base, que el UPDATE condicionado impide reutilizar un paso TOTP o un
 * codigo de recuperacion, que el bloqueo de la fila del desafio deja UNA
 * sesion cuando dos canjes llegan a la vez, que un codigo incorrecto suma al
 * mismo contador de intentos que la contrasena, y el enrolamiento obligatorio
 * de una cuenta administrativa sin sesion.
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
        "app.seguridad.umbral-intentos-fallidos=3",
        "identidad.correo.envio=sincrono",
        // Clave de PRUEBAS (base64 de 32 bytes ASCII), no de ningun entorno.
        "identidad.segundo-factor.clave=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        // La obligatoriedad, encendida solo para el super administrador inicial
        // de esta prueba: los jugadores siguen entrando con la contrasena.
        "identidad.segundo-factor.obligatorio-roles=SUPER_ADMINISTRADOR",
        "app.admins.iniciales=jefa|jefa@upb.edu.co|Clave-De-La-Jefa-2026"
})
@ActiveProfiles("test")
@Testcontainers
@DisplayName("Segundo factor TOTP (HU-AUT-007), con PostgreSQL")
class SegundoFactorIT {

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

    record Respuesta(int estado, String cuerpo) {
    }

    private RestClient cliente() {
        return RestClient.create("http://localhost:" + puerto);
    }

    private Respuesta enviar(RestClient.RequestHeadersSpec<?> peticion) {
        return peticion.exchange((p, r) -> new Respuesta(r.getStatusCode().value(),
                new String(r.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    }

    private Respuesta json(String ruta, String cuerpo, String token) {
        RestClient.RequestBodySpec peticion = cliente().post().uri(ruta).contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT, "application/json, application/problem+json");
        if (token != null) {
            peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return enviar(cuerpo == null ? peticion : peticion.body(cuerpo));
    }

    private Respuesta login(String correo, String clave) {
        return json("/api/v1/auth/login", "{\"email\":\"" + correo + "\",\"password\":\"" + clave + "\"}", null);
    }

    private Respuesta canje(String desafio, String campo, String valor) {
        return json("/api/v1/auth/login/segundo-factor",
                "{\"desafio\":\"" + desafio + "\",\"" + campo + "\":\"" + valor + "\"}", null);
    }

    private static String campo(String json, String nombre) {
        Matcher m = Pattern.compile("\"" + nombre + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private static List<String> codigosDe(String json) {
        Matcher lista = Pattern.compile("\"codigosRecuperacion\"\\s*:\\s*\\[([^\\]]*)]").matcher(json);
        List<String> codigos = new ArrayList<>();
        if (lista.find()) {
            Matcher uno = Pattern.compile("\"([^\"]+)\"").matcher(lista.group(1));
            while (uno.find()) {
                codigos.add(uno.group(1));
            }
        }
        return codigos;
    }

    private static String cuerpoDelToken(String jwt) {
        return new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8);
    }

    /** Registra, confirma el correo y entra (sin segundo factor todavia). */
    private String jugadorActivo(String apodo) {
        MultiValueMap<String, Object> formulario = new LinkedMultiValueMap<>();
        formulario.add("nombres", "Nombre");
        formulario.add("apellidos", "Apellido");
        formulario.add("email", apodo + "@upb.edu.co");
        formulario.add("password", CLAVE);
        formulario.add("apodo", apodo);
        Respuesta alta = enviar(cliente().post().uri("/api/v1/auth/registro").contentType(MediaType.MULTIPART_FORM_DATA)
                .header(HttpHeaders.ACCEPT, "application/json, application/problem+json").body(formulario));
        assertThat(alta.estado()).as(alta.cuerpo()).isEqualTo(201);
        String correo = apodo + "@upb.edu.co";
        List<ServidorFalso.Peticion> enviados = PLATAFORMA.recibidas("POST", "/api/v1/correos/confirmacion-cuenta")
                .stream().filter(p -> p.cuerpo().contains("\"email\":\"" + correo + "\"")).toList();
        String codigo = campo(enviados.get(enviados.size() - 1).cuerpo(), "codigo");
        assertThat(json("/api/v1/auth/verificacion/confirmacion",
                "{\"email\":\"" + correo + "\",\"codigo\":\"" + codigo + "\"}", null).estado()).isEqualTo(200);
        Respuesta sesion = login(correo, CLAVE);
        assertThat(sesion.estado()).as(sesion.cuerpo()).isEqualTo(200);
        return campo(sesion.cuerpo(), "token");
    }

    /** Enrola y activa con la sesion; devuelve el secreto y el paso usado al activar. */
    private record Activado(byte[] secreto, long paso, List<String> codigos) {
    }

    private Activado activar(String token) {
        Respuesta enrolamiento = json("/api/v1/auth/segundo-factor/enrolamiento", null, token);
        assertThat(enrolamiento.estado()).as(enrolamiento.cuerpo()).isEqualTo(200);
        byte[] secreto = Base32.decodificar(campo(enrolamiento.cuerpo(), "secreto"));
        long paso = Totp.pasoDe(Instant.now());
        Respuesta activacion = json("/api/v1/auth/segundo-factor/activacion",
                "{\"codigo\":\"" + Totp.codigo(secreto, paso) + "\"}", token);
        assertThat(activacion.estado()).as(activacion.cuerpo()).isEqualTo(200);
        return new Activado(secreto, paso, codigosDe(activacion.cuerpo()));
    }

    private int intentosDe(String apodo) {
        return jdbc.queryForObject("SELECT intentos_fallidos FROM usuarios WHERE apodo = ?", Integer.class, apodo);
    }

    // ---------------------------------------------------------------- flujo

    @Test
    @DisplayName("activar, entrar en dos pasos, recuperacion de un solo uso y desactivar; nada en claro en la base")
    void flujoCompleto() {
        String token = jugadorActivo("ada");
        Long id = jdbc.queryForObject("SELECT id FROM usuarios WHERE apodo = 'ada'", Long.class);

        Activado activado = activar(token);
        assertThat(activado.codigos()).hasSize(10);

        // El secreto, cifrado; los codigos, con BCrypt.
        String guardado = jdbc.queryForObject("SELECT secreto_cifrado FROM segundo_factor WHERE usuario_id = ?",
                String.class, id);
        assertThat(guardado).startsWith("v1:").doesNotContain(Base32.codificar(activado.secreto()));
        List<String> resumenes = jdbc.queryForList(
                "SELECT codigo_hash FROM codigos_recuperacion WHERE usuario_id = ?", String.class, id);
        assertThat(resumenes).hasSize(10).allMatch(r -> r.startsWith("$2"))
                .noneMatch(r -> r.contains(CodigosDeRecuperacion.normalizar(activado.codigos().get(0))));
        String primerCodigo = CodigosDeRecuperacion.normalizar(activado.codigos().get(0));
        assertThat(resumenes).anyMatch(r -> new BCryptPasswordEncoder().matches(primerCodigo, r));

        // Primer paso: 403 con desafio, sin token; el desafio solo resumido en la base.
        Respuesta primero = login("ada@upb.edu.co", CLAVE);
        assertThat(primero.estado()).isEqualTo(403);
        assertThat(primero.cuerpo()).contains(TIPOS + "segundo-factor-requerido").doesNotContain("\"token\"");
        String desafio = campo(primero.cuerpo(), "desafio");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM desafios_acceso WHERE token_hash = ?", Integer.class,
                DesafiosDeAcceso.resumen(desafio))).isEqualTo(1);

        // Un codigo incorrecto cuenta como intento fallido de la cuenta.
        String malo = Totp.codigo(activado.secreto(), activado.paso() + 1).equals("000000") ? "111111" : "000000";
        Respuesta fallo = canje(desafio, "codigo", malo);
        assertThat(fallo.estado()).isEqualTo(401);
        assertThat(fallo.cuerpo()).contains(TIPOS + "codigo-segundo-factor-invalido");
        assertThat(intentosDe("ada")).isEqualTo(1);

        // El codigo del paso de la activacion ya no vale (sin repeticion)...
        Respuesta repetido = canje(desafio, "codigo", Totp.codigo(activado.secreto(), activado.paso()));
        assertThat(repetido.estado()).isEqualTo(401);
        assertThat(intentosDe("ada")).isEqualTo(2);

        // ...el siguiente si, y la sesion sale con amr pwd+otp y los intentos a cero.
        Respuesta dentro = canje(desafio, "codigo", Totp.codigo(activado.secreto(), activado.paso() + 1));
        assertThat(dentro.estado()).as(dentro.cuerpo()).isEqualTo(200);
        assertThat(cuerpoDelToken(campo(dentro.cuerpo(), "token"))).contains("\"amr\":[\"pwd\",\"otp\"]");
        assertThat(intentosDe("ada")).isZero();
        // El desafio ya se gasto.
        assertThat(canje(desafio, "codigo", Totp.codigo(activado.secreto(), activado.paso() + 2)).cuerpo())
                .contains(TIPOS + "desafio-invalido");

        // Con un codigo de recuperacion: entra una vez y dice cuantos quedan.
        String desafio2 = campo(login("ada@upb.edu.co", CLAVE).cuerpo(), "desafio");
        Respuesta conRecuperacion = canje(desafio2, "codigoRecuperacion", activado.codigos().get(3).toLowerCase());
        assertThat(conRecuperacion.estado()).as(conRecuperacion.cuerpo()).isEqualTo(200);
        assertThat(conRecuperacion.cuerpo()).contains("\"codigosRecuperacionRestantes\":9");
        String desafio3 = campo(login("ada@upb.edu.co", CLAVE).cuerpo(), "desafio");
        assertThat(canje(desafio3, "codigoRecuperacion", activado.codigos().get(3)).estado()).isEqualTo(401);

        // Desactivar: contrasena + codigo de recuperacion; despues el login es el de siempre.
        String sesion = campo(conRecuperacion.cuerpo(), "token");
        Respuesta desactivacion = json("/api/v1/auth/segundo-factor/desactivacion",
                "{\"passwordActual\":\"" + CLAVE + "\",\"codigoRecuperacion\":\"" + activado.codigos().get(5) + "\"}",
                sesion);
        assertThat(desactivacion.estado()).as(desactivacion.cuerpo()).isEqualTo(204);
        Respuesta sinSegundoFactor = login("ada@upb.edu.co", CLAVE);
        assertThat(sinSegundoFactor.estado()).isEqualTo(200);
        assertThat(cuerpoDelToken(campo(sinSegundoFactor.cuerpo(), "token"))).contains("\"amr\":[\"pwd\"]");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM codigos_recuperacion WHERE usuario_id = ?",
                Integer.class, id)).isZero();
    }

    @Test
    @DisplayName("dos canjes simultaneos del mismo desafio: una sola sesion")
    void canjesSimultaneos() throws Exception {
        String token = jugadorActivo("bea");
        Activado activado = activar(token);
        String desafio = campo(login("bea@upb.edu.co", CLAVE).cuerpo(), "desafio");
        String codigo = Totp.codigo(activado.secreto(), activado.paso() + 1);

        ExecutorService hilos = Executors.newFixedThreadPool(2);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Respuesta>> canjes = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            canjes.add(hilos.submit(() -> {
                salida.await();
                return canje(desafio, "codigo", codigo);
            }));
        }
        salida.countDown();
        List<Integer> estados = new ArrayList<>();
        for (Future<Respuesta> canje : canjes) {
            estados.add(canje.get().estado());
        }
        hilos.shutdown();

        assertThat(estados).containsExactlyInAnyOrder(200, 401);
    }

    @Test
    @DisplayName("tres codigos incorrectos bloquean la cuenta como tres contrasenas: 423 y no se compara nada")
    void bloqueoPorCodigos() {
        String token = jugadorActivo("cris");
        activar(token);
        String desafio = campo(login("cris@upb.edu.co", CLAVE).cuerpo(), "desafio");

        for (int i = 0; i < 3; i++) {
            assertThat(canje(desafio, "codigo", "000000").estado()).isEqualTo(401);
        }
        Respuesta bloqueada = canje(desafio, "codigo", "000000");
        assertThat(bloqueada.estado()).isEqualTo(423);
        assertThat(bloqueada.cuerpo()).contains(TIPOS + "cuenta-bloqueada");
        // Y el login con la contrasena correcta tambien queda bloqueado.
        assertThat(login("cris@upb.edu.co", CLAVE).estado()).isEqualTo(423);
    }

    @Test
    @DisplayName("rol obligatorio sin segundo factor: enrolarse sin sesion y entrar con doble factor")
    void enrolamientoObligatorio() {
        Respuesta primero = login("jefa@upb.edu.co", "Clave-De-La-Jefa-2026");
        assertThat(primero.estado()).as(primero.cuerpo()).isEqualTo(403);
        assertThat(primero.cuerpo()).contains(TIPOS + "segundo-factor-enrolamiento-requerido")
                .doesNotContain("\"token\"");
        String desafio = campo(primero.cuerpo(), "desafio");

        // Con este desafio no se puede canjear un codigo: es de enrolamiento.
        assertThat(canje(desafio, "codigo", "123456").cuerpo()).contains(TIPOS + "desafio-invalido");

        Respuesta enrolamiento = json("/api/v1/auth/login/segundo-factor/enrolamiento",
                "{\"desafio\":\"" + desafio + "\"}", null);
        assertThat(enrolamiento.estado()).as(enrolamiento.cuerpo()).isEqualTo(200);
        byte[] secreto = Base32.decodificar(campo(enrolamiento.cuerpo(), "secreto"));
        long paso = Totp.pasoDe(Instant.now());

        Respuesta activacion = json("/api/v1/auth/login/segundo-factor/activacion",
                "{\"desafio\":\"" + desafio + "\",\"codigo\":\"" + Totp.codigo(secreto, paso) + "\"}", null);
        assertThat(activacion.estado()).as(activacion.cuerpo()).isEqualTo(200);
        assertThat(codigosDe(activacion.cuerpo())).hasSize(10);
        assertThat(cuerpoDelToken(campo(activacion.cuerpo(), "token"))).contains("\"amr\":[\"pwd\",\"otp\"]")
                .contains("\"rol\":\"SUPER_ADMINISTRADOR\"");

        // Desde ahora le pide el codigo, como a cualquier cuenta con segundo factor.
        assertThat(login("jefa@upb.edu.co", "Clave-De-La-Jefa-2026").cuerpo())
                .contains(TIPOS + "segundo-factor-requerido");
        // Y un jugador, con la obligatoriedad encendida solo para su rol, entra como siempre.
        assertThat(jugadorActivo("dani")).isNotBlank();
    }
}
