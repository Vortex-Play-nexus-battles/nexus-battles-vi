package com.nexusbattles.comun.seguridad.servicio.it;

import com.nexusbattles.comun.seguridad.servicio.InterceptorDePortadorDeServicio;
import com.nexusbattles.comun.seguridad.servicio.TokenDeServicio;
import com.nexusbattles.comun.seguridad.servicio.TokenDeServicioOAuth2;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-001 de punta a punta, con las tres piezas reales:
 *
 * <pre>
 *   Keycloak (Testcontainers, realm de prueba)
 *        │ client_credentials
 *        ▼
 *   TokenDeServicioOAuth2 (el «ms-subastas» de la prueba)
 *        │ Authorization: Bearer <token de servicio>
 *        ▼
 *   AplicacionDePrueba (el «inventario» de la prueba, servidor de recursos)
 *        │ hasRole(<rol de servicio>) → opera sobre propietarioUid del cuerpo
 * </pre>
 *
 * <p>Nada se simula: el emisor firma, el servidor valida contra el JWKS del
 * realm, y el actor se lee del claim {@code azp}. Ni inventario ni subastas se
 * tocan; sus adaptadores hacen exactamente lo que hace esta prueba.
 *
 * <p>Sin {@code disabledWithoutDocker}: una prueba de integracion omitida no es
 * una prueba que pasa (misma regla que en salas-partidas).
 */
@Testcontainers
@SpringBootTest(classes = AplicacionDePrueba.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Patron servicio-a-servicio de punta a punta (ADR-001)")
class PatronServicioAServicioIT {

    private static final String REALM = "nexus-battles";

    @Container
    static final GenericContainer<?> KEYCLOAK = new GenericContainer<>("quay.io/keycloak/keycloak:26.0")
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("keycloak/realm-de-prueba.json"),
                    "/opt/keycloak/data/import/realm-de-prueba.json")
            .withCommand("start-dev", "--import-realm")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/realms/" + REALM).forPort(8080).forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(4)));

    static String urlDelRealm() {
        return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080) + "/realms/" + REALM;
    }

    static String urlBase() {
        return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080);
    }

    /**
     * Asigna el rol de servicio a la cuenta de servicio de {@code ms-subastas}.
     *
     * <p>El realm importado trae el rol y los dos clientes, pero la asignacion
     * del rol a la cuenta de servicio no sobrevive al {@code --import-realm}:
     * Keycloak crea esa cuenta al crear el cliente y no aplica los
     * {@code realmRoles} de un usuario con {@code serviceAccountClientId}. Se
     * hace por la API de administracion, que es lo que hara quien administre
     * el realm real. {@code otro-servicio} se queda sin rol a proposito.
     */
    @BeforeAll
    static void asignarRolDeServicioASubastas() {
        RestClient admin = RestClient.builder().baseUrl(urlBase()).build();

        Map<String, Object> sesion = admin.post()
                .uri("/realms/master/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=password&client_id=admin-cli&username=admin&password=admin")
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() { });
        String portador = "Bearer " + sesion.get("access_token");

        List<Map<String, Object>> clientes = admin.get()
                .uri("/admin/realms/{realm}/clients?clientId=ms-subastas", REALM)
                .header("Authorization", portador)
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() { });
        String idDelCliente = String.valueOf(clientes.get(0).get("id"));

        Map<String, Object> cuentaDeServicio = admin.get()
                .uri("/admin/realms/{realm}/clients/{id}/service-account-user", REALM, idDelCliente)
                .header("Authorization", portador)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() { });

        Map<String, Object> rol = admin.get()
                .uri("/admin/realms/{realm}/roles/{rol}", REALM, AplicacionDePrueba.ROL_DE_SERVICIO_PROVISIONAL)
                .header("Authorization", portador)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() { });

        admin.post()
                .uri("/admin/realms/{realm}/users/{usuario}/role-mappings/realm", REALM, cuentaDeServicio.get("id"))
                .header("Authorization", portador)
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(Map.of("id", rol.get("id"), "name", rol.get("name"))))
                .retrieve()
                .toBodilessEntity();
    }

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registro) {
        // Servidor de recursos: valida contra el emisor real.
        registro.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                PatronServicioAServicioIT::urlDelRealm);
        // Cliente: las mismas tres variables de DIRECTORIO_ACTIVO_*, por propiedad.
        registro.add("seguridad.servicio.url", PatronServicioAServicioIT::urlDelRealm);
        registro.add("seguridad.servicio.client-id", () -> "ms-subastas");
        registro.add("seguridad.servicio.client-secret", () -> "secreto-de-prueba-ms-subastas");
    }

    @Autowired
    private List<SecurityFilterChain> cadenas;

    @Test
    @DisplayName("el servidor de recursos usa una sola cadena, la de la plataforma: sin CSRF ni sesion")
    void unaSolaCadenaSinCsrf() {
        // Un PUT con token valido no debe morir por CSRF: aplicarBase lo desactiva
        // porque no hay sesion de navegador que proteger.
        List<String> filtros = cadenas.stream()
                .flatMap(c -> c.getFilters().stream())
                .map(f -> f.getClass().getSimpleName())
                .toList();
        assertThat(cadenas).hasSize(1);
        assertThat(filtros).contains("BearerTokenAuthenticationFilter").doesNotContain("CsrfFilter");
    }

    @Value("${local.server.port}")
    private int puerto;

    /** Lo que la autoconfiguracion crea para «ms-subastas». */
    @Autowired
    private TokenDeServicio tokenDeSubastas;

    @Autowired
    private InterceptorDePortadorDeServicio interceptorDeSubastas;

    private static final ParameterizedTypeReference<Map<String, String>> MAPA =
            new ParameterizedTypeReference<>() { };

    private static final Map<String, String> BLOQUEO = Map.of(
            "subastaId", "aaaaaaaa-0000-0000-0000-000000000001",
            "propietarioUid", "22222222-2222-2222-2222-222222222222");

    private RestClient clienteSinCredencial() {
        return RestClient.builder().baseUrl("http://localhost:" + puerto).build();
    }

    private RestClient clienteDeSubastas() {
        return RestClient.builder().baseUrl("http://localhost:" + puerto)
                .requestInterceptor(interceptorDeSubastas).build();
    }

    private ResponseEntity<Map<String, String>> bloquear(RestClient cliente) {
        return cliente.put()
                .uri("/api/v1/prueba/elementos/{id}/bloqueo-subasta", "elem-1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BLOQUEO)
                .exchange((peticion, respuesta) -> {
                    if (respuesta.getStatusCode().is2xxSuccessful()) {
                        return ResponseEntity.status(respuesta.getStatusCode())
                                .body(respuesta.bodyTo(MAPA));
                    }
                    // En un fallo, el cuerpo dice por que: se conserva para el mensaje de la asercion.
                    String detalle = respuesta.bodyTo(String.class);
                    return ResponseEntity.status(respuesta.getStatusCode())
                            .body(Map.of("error", detalle == null ? "" : detalle));
                });
    }

    @Test
    @DisplayName("sin token, 401: la operacion interna no es publica")
    void sinTokenEs401() {
        assertThat(bloquear(clienteSinCredencial()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("un servicio autenticado pero sin el rol, 403: identidad verificada, permiso denegado")
    void otroServicioEs403() {
        TokenDeServicio otro = new TokenDeServicioOAuth2(urlDelRealm(), "otro-servicio",
                "secreto-de-prueba-otro-servicio", Clock.systemUTC());
        RestClient cliente = RestClient.builder().baseUrl("http://localhost:" + puerto)
                .requestInterceptor(new InterceptorDePortadorDeServicio(otro)).build();

        assertThat(bloquear(cliente).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    /** Payload del JWT en claro, solo para que un fallo diga QUE trae el token. */
    private static String claimsDe(String token) {
        String[] partes = token.split("\\.");
        return new String(java.util.Base64.getUrlDecoder().decode(partes[1]), java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("el token de ms-subastas trae azp y el rol de servicio en realm_access")
    void elTokenTraeActorYRol() {
        String claims = claimsDe(tokenDeSubastas.portador());

        assertThat(claims).as("claims del token de servicio").contains("\"azp\":\"ms-subastas\"");
        assertThat(claims).as("claims del token de servicio")
                .contains(AplicacionDePrueba.ROL_DE_SERVICIO_PROVISIONAL);
    }

    @Test
    @DisplayName("ms-subastas con su rol, 200: el actor es el servicio y el propietario es el del cuerpo")
    void subastasAutorizadoOperaSobreElPropietarioDelCuerpo() {
        ResponseEntity<Map<String, String>> respuesta = bloquear(clienteDeSubastas());

        assertThat(respuesta.getStatusCode())
                .as("respuesta: %s · claims del token: %s", respuesta.getBody(),
                        claimsDe(tokenDeSubastas.portador()))
                .isEqualTo(HttpStatus.OK);
        Map<String, String> cuerpo = respuesta.getBody();
        assertThat(cuerpo).isNotNull();
        assertThat(cuerpo.get("actor")).isEqualTo("ms-subastas");
        // El propietario NO sale del token (el token no tiene jugador): sale del cuerpo.
        assertThat(cuerpo.get("propietarioUid")).isEqualTo("22222222-2222-2222-2222-222222222222");
        assertThat(cuerpo.get("subastaId")).isEqualTo("aaaaaaaa-0000-0000-0000-000000000001");
    }

    @Test
    @DisplayName("desde un hilo de job, sin SecurityContext ni peticion original, tambien 200")
    void desdeUnJobSinContexto() throws Exception {
        ExecutorService job = Executors.newSingleThreadExecutor(r -> new Thread(r, "cierre-de-subastas"));
        try {
            Future<HttpStatus> estado = job.submit(() -> {
                assertThat(SecurityContextHolder.getContext().getAuthentication())
                        .as("un job no tiene usuario autenticado")
                        .isNull();
                return (HttpStatus) bloquear(clienteDeSubastas()).getStatusCode();
            });
            assertThat(estado.get(30, TimeUnit.SECONDS)).isEqualTo(HttpStatus.OK);
        } finally {
            job.shutdownNow();
        }
    }

    @Test
    @DisplayName("el reintento reutiliza la misma credencial vigente: no vuelve al emisor por cada llamada")
    void elReintentoReutilizaLaCredencial() {
        String primera = tokenDeSubastas.portador();
        bloquear(clienteDeSubastas());
        bloquear(clienteDeSubastas());

        assertThat(tokenDeSubastas.portador()).isEqualTo(primera);
    }
}
