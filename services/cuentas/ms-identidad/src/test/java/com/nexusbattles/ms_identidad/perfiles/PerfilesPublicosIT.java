package com.nexusbattles.ms_identidad.perfiles;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code GET /api/v1/perfiles/publicos} de punta a punta con PostgreSQL de
 * verdad (Flyway V1..V4 y Hibernate en validate), Tomcat, el
 * {@code SecurityInterceptor} y una sesion obtenida por el login real.
 *
 * <p>Lo que demuestra y ninguna prueba con dobles puede: que la consulta que
 * genera Hibernate hace en PostgreSQL lo que el contrato promete —prefijo sin
 * distinguir mayusculas, {@code %} y {@code _} literales, solo cuentas ACTIVO,
 * orden por apodo y tope de 10— y que la respuesta no arrastra ningun dato
 * que no sea publico.
 *
 * <p>Cada prueba siembra sus cuentas con una marca propia al principio del
 * apodo: la base es compartida por toda la clase y la busqueda es por
 * prefijo, asi que la marca es lo que aisla una prueba de otra.
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
        "identidad.correo.envio=sincrono"
})
@ActiveProfiles("test")
@Testcontainers
@DisplayName("Busqueda de jugadores por apodo (GET /api/v1/perfiles/publicos), con PostgreSQL")
class PerfilesPublicosIT {

    private static final String RUTA = "/api/v1/perfiles/publicos";
    private static final String CLAVE = "Segura-123!";
    private static final String CLAVE_CIFRADA = new BCryptPasswordEncoder().encode(CLAVE);
    private static final ObjectMapper JSON = new ObjectMapper();

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> BASE = new PostgreSQLContainer<>("postgres:15-alpine");

    /** Correo, notificaciones y auditoria: lo que el login avisa al entrar. */
    static final ServidorFalso PLATAFORMA = new ServidorFalso();

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
    }

    @AfterAll
    static void apagar() {
        PLATAFORMA.close();
    }

    @LocalServerPort
    private int puerto;

    @Autowired
    private JdbcTemplate jdbc;

    /** Marca de esta prueba: ningun otro apodo de la base empieza asi. */
    private String marca;

    @BeforeEach
    void preparar() {
        PLATAFORMA.responder("POST", "/api/v1/correos/.*", 202, "")
                .responder("POST", "/api/v1/internal/notifications", 202, "{}")
                .responder("POST", "/api/v1/admin/auditoria/eventos", 201, "{}");
        StringBuilder letras = new StringBuilder("zq");
        for (int i = 0; i < 5; i++) {
            letras.append((char) ('a' + ThreadLocalRandom.current().nextInt(26)));
        }
        marca = letras.toString();
    }

    // ------------------------------------------------------------ ayudantes

    record Respuesta(int estado, String cuerpo, HttpHeaders cabeceras) {

        JsonNode json() {
            try {
                return JSON.readTree(cuerpo);
            } catch (Exception e) {
                throw new AssertionError("no es JSON: " + cuerpo, e);
            }
        }

        List<String> apodos() {
            List<String> apodos = new ArrayList<>();
            json().forEach(nodo -> apodos.add(nodo.get("apodo").asText()));
            return apodos;
        }
    }

    /** Una sesion abierta con el login real: el token y el uid de quien busca. */
    record Sesion(String token, UUID uid) {
    }

    private RestClient cliente() {
        return RestClient.create("http://localhost:" + puerto);
    }

    private Respuesta enviar(RestClient.RequestHeadersSpec<?> peticion) {
        return peticion.exchange((p, r) -> new Respuesta(r.getStatusCode().value(),
                new String(r.getBody().readAllBytes(), StandardCharsets.UTF_8), r.getHeaders()));
    }

    /**
     * La busqueda, con el texto codificado como lo haria el navegador
     * ({@code %} viaja como {@code %25}): lo que se prueba es que el servidor
     * no lo trate como comodin, no que el cliente lo mande mal.
     */
    private Respuesta buscar(String texto, String token) {
        URI uri = URI.create("http://localhost:" + puerto + RUTA + "?apodo="
                + URLEncoder.encode(texto, StandardCharsets.UTF_8));
        RestClient.RequestHeadersSpec<?> peticion = cliente().get().uri(uri)
                .header(HttpHeaders.ACCEPT, "application/json, application/problem+json");
        if (token != null) {
            peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return enviar(peticion);
    }

    private Long rol(String nombre) {
        return jdbc.queryForObject("SELECT id FROM roles WHERE nombre = ?", Long.class, nombre);
    }

    /** Una cuenta tal como la dejan el registro y la verificacion; sin perfil si {@code avatar} es null. */
    private UUID cuenta(String apodo, String estado, String rol, String avatar) {
        UUID uid = UUID.randomUUID();
        jdbc.update("INSERT INTO usuarios (public_id, apodo, email, password, estado, rol_id, intentos_fallidos,"
                        + " version_token, creado_en) VALUES (?, ?, ?, ?, ?, ?, 0, 0, ?)",
                uid, apodo, "c" + UUID.randomUUID().toString().substring(0, 12) + "@upb.edu.co", CLAVE_CIFRADA,
                estado, rol(rol), Timestamp.valueOf(LocalDateTime.now()));
        if (avatar != null) {
            jdbc.update("INSERT INTO perfiles_usuario (usuario_id, nombres, apellidos, avatar)"
                    + " SELECT id, 'Nombre secreto', 'Apellido secreto', ? FROM usuarios WHERE public_id = ?",
                    avatar, uid);
        }
        return uid;
    }

    private UUID jugador(String apodo) {
        return cuenta(apodo, "ACTIVO", "JUGADOR", "/avatares-subidos/" + apodo + ".png");
    }

    /** Quien busca: una cuenta ACTIVA fuera de la marca, con sesion del login real. */
    private Sesion sesion() {
        String apodo = "lector" + UUID.randomUUID().toString().substring(0, 8);
        UUID uid = cuenta(apodo, "ACTIVO", "JUGADOR", "/avatares-subidos/lector.png");
        String correo = jdbc.queryForObject("SELECT email FROM usuarios WHERE public_id = ?", String.class, uid);
        Respuesta login = enviar(cliente().post().uri("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body("{\"email\":\"" + correo + "\",\"password\":\"" + CLAVE + "\"}"));
        assertThat(login.estado()).as(login.cuerpo()).isEqualTo(200);
        return new Sesion(login.json().get("token").asText(), uid);
    }

    /** Solo el token: la mayoria de las pruebas no necesita mas. */
    private String token() {
        return sesion().token();
    }

    // --------------------------------------------------------------- pruebas

    @Test
    @DisplayName("prefijo sin distinguir mayusculas: «ZQ…» encuentra «zq…» y «Zq…», no a quien solo lo contiene")
    void prefijoSinMayusculas() {
        String token = token();
        UUID ana = jugador(marca + "ana");
        jugador(marca.toUpperCase(Locale.ROOT) + "BETO");
        jugador("Zq" + marca.substring(2) + "Carla");
        jugador("x" + marca + "dentro");

        Respuesta respuesta = buscar(marca.toUpperCase(Locale.ROOT), token);

        assertThat(respuesta.estado()).as(respuesta.cuerpo()).isEqualTo(200);
        assertThat(respuesta.cabeceras().getContentType()).isNotNull();
        assertThat(respuesta.cabeceras().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
        assertThat(respuesta.apodos()).containsExactly(marca + "ana", marca.toUpperCase(Locale.ROOT) + "BETO",
                "Zq" + marca.substring(2) + "Carla");
        JsonNode primero = respuesta.json().get(0);
        assertThat(primero.get("uid").asText()).isEqualTo(ana.toString());
        assertThat(primero.get("avatar").asText()).isEqualTo("/avatares-subidos/" + marca + "ana.png");

        assertThat(buscar(marca + "an", token).apodos()).containsExactly(marca + "ana");
        assertThat(buscar("  " + marca + "an  ", token).apodos()).as("los espacios alrededor no cuentan")
                .containsExactly(marca + "ana");
    }

    @Test
    @DisplayName("como mucho 10, ordenados por apodo sin distinguir mayusculas")
    void diezOrdenados() {
        String token = token();
        List<String> sembrados = new ArrayList<>();
        for (int i = 11; i >= 0; i--) {
            String apodo = marca + (i % 2 == 0 ? "J" : "j") + String.format("%02d", i);
            jugador(apodo);
            sembrados.add(apodo);
        }
        List<String> esperados = sembrados.stream()
                .sorted((a, b) -> a.toLowerCase(Locale.ROOT).compareTo(b.toLowerCase(Locale.ROOT)))
                .limit(10)
                .toList();

        Respuesta respuesta = buscar(marca, token);

        assertThat(respuesta.estado()).isEqualTo(200);
        assertThat(respuesta.apodos()).hasSize(10).containsExactlyElementsOf(esperados);
    }

    @Test
    @DisplayName("solo cuentas ACTIVO: ni pendiente de verificar, ni inactiva, ni suspendida, ni baneada")
    void soloActivas() {
        String token = token();
        jugador(marca + "activa");
        cuenta(marca + "admin", "ACTIVO", "ADMINISTRADOR", null);
        cuenta(marca + "pendiente", "PENDIENTE_VERIFICACION", "JUGADOR", "/avatares-subidos/p.png");
        cuenta(marca + "inactiva", "INACTIVO", "MODERADOR", null);
        cuenta(marca + "suspendida", "SUSPENDIDO", "JUGADOR", "/avatares-subidos/s.png");
        cuenta(marca + "baneada", "BANEADO", "JUGADOR", "/avatares-subidos/b.png");
        cuenta(marca + "suspendidavieja", "SUSPENDIDA", "JUGADOR", null);
        cuenta(marca + "baneadavieja", "BANEADA", "JUGADOR", null);

        Respuesta respuesta = buscar(marca, token);

        assertThat(respuesta.estado()).isEqualTo(200);
        assertThat(respuesta.apodos()).containsExactly(marca + "activa", marca + "admin");
        JsonNode avatarSinPerfil = respuesta.json().get(1).get("avatar");
        assertThat(avatarSinPerfil == null || avatarSinPerfil.isNull())
                .as("una cuenta sin perfil (las administrativas) sale sin avatar").isTrue();
    }

    @Test
    @DisplayName("la respuesta solo trae uid, apodo y avatar: ni correo, ni nombres, ni estado, ni id interno")
    void noExponeDatosPrivados() {
        String token = token();
        UUID uid = jugador(marca + "privada");
        String correo = jdbc.queryForObject("SELECT email FROM usuarios WHERE public_id = ?", String.class, uid);

        Respuesta respuesta = buscar(marca, token);

        assertThat(respuesta.estado()).isEqualTo(200);
        JsonNode perfil = respuesta.json().get(0);
        List<String> campos = new ArrayList<>();
        perfil.fieldNames().forEachRemaining(campos::add);
        assertThat(campos).containsExactlyInAnyOrder("uid", "apodo", "avatar");
        assertThat(respuesta.cuerpo()).doesNotContain(correo).doesNotContain("@")
                .doesNotContain("secreto").doesNotContain("ACTIVO").doesNotContain("password")
                .doesNotContain("$2");
    }

    @Test
    @DisplayName("% y _ se buscan literalmente: «zq…_» no es «zq… + cualquier letra», ni «zq…%» es «todo»")
    void comodinesEscapados() {
        String token = token();
        jugador(marca + "_uno");
        jugador(marca + "xuno");
        jugador(marca + "%dos");
        jugador(marca + "abcdos");
        jugador(marca + "!tres");
        jugador(marca + "tres");

        assertThat(buscar(marca + "_", token).apodos()).containsExactly(marca + "_uno");
        assertThat(buscar(marca + "%", token).apodos()).containsExactly(marca + "%dos");
        assertThat(buscar(marca + "!", token).apodos()).as("el caracter de escape tambien es literal")
                .containsExactly(marca + "!tres");
        assertThat(buscar("%%%", token).json()).as("tres % no listan la base: nadie se llama asi").isEmpty();
        assertThat(buscar("___", token).json()).as("ni tres _").isEmpty();
    }

    @Test
    @DisplayName("sin sesion: 403 problem details; con la cabecera de rol en vez de token, tambien")
    void sinSesion() {
        jugador(marca + "visible");

        Respuesta anonima = buscar(marca, null);
        assertThat(anonima.estado()).isEqualTo(403);
        assertThat(anonima.cabeceras().getContentType()).isNotNull();
        assertThat(anonima.cabeceras().getContentType().toString()).startsWith("application/problem+json");
        assertThat(anonima.cuerpo()).contains("https://nexusbattles.upb.edu.co/errors/forbidden")
                .doesNotContain(marca + "visible");

        Respuesta conCabecera = enviar(cliente().get().uri(RUTA + "?apodo=" + marca)
                .header("X-User-Name", "alguien").header("X-User-Role", "SUPER_ADMINISTRADOR"));
        assertThat(conCabecera.estado()).isEqualTo(403);

        assertThat(buscar(marca, "no-es-un-jwt").estado()).isEqualTo(403);
    }

    @Test
    @DisplayName("menos de 3 caracteres, o sin el parametro: 400 problem details datos-invalidos")
    void demasiadoCorto() {
        String token = token();

        Respuesta corta = buscar("zq", token);
        assertThat(corta.estado()).isEqualTo(400);
        assertThat(corta.cabeceras().getContentType()).isNotNull();
        assertThat(corta.cabeceras().getContentType().toString()).startsWith("application/problem+json");
        assertThat(corta.json().get("type").asText()).isEqualTo("https://nexusbattles.upb.edu.co/errors/datos-invalidos");
        assertThat(corta.json().get("status").asInt()).isEqualTo(400);

        assertThat(buscar("  zq  ", token).estado()).as("los espacios no suman caracteres").isEqualTo(400);
        assertThat(buscar("z".repeat(51), token).estado()).as("mas largo que cualquier apodo").isEqualTo(400);

        Respuesta sinParametro = enviar(cliente().get().uri(RUTA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
        assertThat(sinParametro.estado()).isEqualTo(400);
        assertThat(sinParametro.json().get("type").asText())
                .isEqualTo("https://nexusbattles.upb.edu.co/errors/datos-invalidos");
    }

    @Test
    @DisplayName("/publicos y /{usuario} no se pisan: la busqueda responde y el perfil propio tambien")
    void rutasSinPisarse() {
        Sesion lector = sesion();
        jugador(marca + "otro");

        Respuesta busqueda = buscar(marca, lector.token());
        assertThat(busqueda.estado()).isEqualTo(200);
        assertThat(busqueda.apodos()).containsExactly(marca + "otro");

        Respuesta propio = enviar(cliente().get().uri("/api/v1/perfiles/" + lector.uid())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + lector.token()));
        assertThat(propio.estado()).as(propio.cuerpo()).isEqualTo(200);
        assertThat(propio.json().get("apodo").asText()).startsWith("lector");

        Respuesta ajeno = enviar(cliente().get().uri("/api/v1/perfiles/"
                        + jdbc.queryForObject("SELECT public_id FROM usuarios WHERE apodo = ?", UUID.class, marca + "otro"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + lector.token()));
        assertThat(ajeno.estado()).as("el perfil completo de otro sigue siendo solo de su dueno").isEqualTo(403);
    }

    @Test
    @DisplayName("V4: el indice de prefijo existe y PostgreSQL lo puede usar para esta forma de consulta")
    void indiceDePrefijo() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE tablename = 'usuarios'"
                + " AND indexname = 'ix_usuarios_apodo_prefijo'", Integer.class)).isEqualTo(1);

        // La misma sentencia que genera Hibernate para buscarPublicosPorPrefijo
        // (copiada del registro con show-sql), con sus parametros ligados como
        // los manda la aplicacion. Con la tabla casi vacia el planificador
        // prefiere recorrerla entera; se le prohibe para preguntarle si PODRIA
        // usar el indice: si alguien cambia la consulta a ILIKE o a '%texto%',
        // o el indice pierde text_pattern_ops, esto se pone rojo.
        String sentenciaDeHibernate = "select u1_0.public_id,u1_0.apodo,pu1_0.avatar from usuarios u1_0"
                + " left join perfiles_usuario pu1_0 on pu1_0.usuario_id=u1_0.id"
                + " where u1_0.estado=? and u1_0.public_id is not null and lower(u1_0.apodo) like lower(?) escape '!'"
                + " order by lower(u1_0.apodo),u1_0.apodo fetch first ? rows only";
        List<String> plan = jdbc.execute((Connection conexion) -> {
            try (Statement ajuste = conexion.createStatement()) {
                ajuste.execute("SET enable_seqscan = off");
                try (PreparedStatement explicar = conexion.prepareStatement("EXPLAIN " + sentenciaDeHibernate)) {
                    explicar.setString(1, "ACTIVO");
                    explicar.setString(2, "zq!_x%");
                    explicar.setInt(3, 10);
                    List<String> lineas = new ArrayList<>();
                    try (ResultSet filas = explicar.executeQuery()) {
                        while (filas.next()) {
                            lineas.add(filas.getString(1));
                        }
                    }
                    return lineas;
                } finally {
                    ajuste.execute("RESET enable_seqscan");
                }
            }
        });
        assertThat(String.join("\n", plan)).contains("ix_usuarios_apodo_prefijo");
    }
}
