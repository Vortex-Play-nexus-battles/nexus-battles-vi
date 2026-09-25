package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sanciones unificadas (7.3.2) de punta a punta dentro del servicio: la
 * sancion se guarda con sus salidas en PostgreSQL y el entregador las lleva a
 * un doble HTTP que habla como ms-identidad (token de servicio,
 * estado-sancion, contacto), correo y notificaciones.
 *
 * <p>Lo que se afirma es lo que otros servicios van a recibir: la credencial
 * de servicio en cada llamada, la forma de cada contrato, la clave de
 * idempotencia del correo, y que nada se pierde si identidad esta caida.
 *
 * <p>El entregador programado no corre (15 s x 240): cada prueba lo llama a
 * mano para saber exactamente que vuelta mira.
 */
@Testcontainers
@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "sanciones.avisos.reintento-ms=3600000"})
@DisplayName("Salidas de sancion · proyeccion, correo y aviso contra dobles de sus contratos")
class SalidasDeSancionIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);

    record Peticion(String metodo, String ruta, String autorizacion, String idempotencia, String cuerpo) {
    }

    static final List<Peticion> RECIBIDAS = new CopyOnWriteArrayList<>();
    static volatile int estadoDeIdentidad = 200;
    static final HttpServer DOBLES = levantarDobles();

    @DynamicPropertySource
    static void destinos(DynamicPropertyRegistry registro) {
        EmisorDeTokensDePrueba.registrarJwks(registro);
        String base = "http://127.0.0.1:" + DOBLES.getAddress().getPort();
        registro.add("seguridad.servicio.url", () -> base + "/api/v1/auth/token");
        registro.add("seguridad.servicio.client-id", () -> "moderacion-sanciones");
        registro.add("seguridad.servicio.client-secret", () -> "secreto-de-prueba-de-moderacion");
        registro.add("sanciones.identidad.url", () -> base);
        registro.add("sanciones.correo.url", () -> base);
        registro.add("sanciones.notificaciones.url", () -> base + "/api/v1");
    }

    @Autowired
    private SancionesService sanciones;

    @Autowired
    private EntregadorDeSalidas entregador;

    @Autowired
    private SalidaPendienteRepository salidas;

    @Autowired
    private JdbcTemplate jdbc;

    private final JsonMapper json = JsonMapper.builder().build();
    private final Actor admin = new Actor(UUID.randomUUID(), "ADMINISTRADOR");

    @BeforeEach
    void limpiar() {
        RECIBIDAS.clear();
        estadoDeIdentidad = 200;
    }

    @AfterEach
    void restaurar() {
        estadoDeIdentidad = 200;
    }

    @AfterAll
    static void apagar() {
        DOBLES.stop(0);
    }

    private List<Peticion> recibidas(String metodo, String fragmento) {
        return RECIBIDAS.stream().filter(p -> p.metodo().equals(metodo) && p.ruta().contains(fragmento)).toList();
    }

    @Test
    @DisplayName("una suspension proyecta SUSPENDIDO en identidad, manda el correo y el aviso, todo con credencial de servicio")
    void suspension() throws Exception {
        UUID jugador = UUID.randomUUID();
        Sancion suspension = sanciones.emitir(admin, new SancionesService.SolicitudDeSancion(jugador,
                Sancion.Tipo.SUSPENSION, "Lenguaje ofensivo", null, null, 48L, false));

        assertThat(entregador.ejecutar()).isGreaterThanOrEqualTo(3);

        Peticion proyeccion = recibidas("PUT", "/api/v1/internal/usuarios/" + jugador + "/estado-sancion").get(0);
        JsonNode cuerpo = json.readTree(proyeccion.cuerpo());
        assertThat(proyeccion.autorizacion()).isEqualTo("Bearer token-de-moderacion");
        assertThat(cuerpo.path("estado").asString()).isEqualTo("SUSPENDIDO");
        assertThat(cuerpo.path("sancionId").asString()).isEqualTo(suspension.id().toString());
        assertThat(cuerpo.path("hasta").asString()).isNotBlank();
        assertThat(cuerpo.path("motivo").asString()).isEqualTo("Lenguaje ofensivo");

        assertThat(recibidas("GET", "/api/v1/internal/usuarios/" + jugador + "/contacto")).hasSize(1);
        Peticion correo = recibidas("POST", "/api/v1/correos/sancion").get(0);
        JsonNode mensaje = json.readTree(correo.cuerpo());
        assertThat(correo.autorizacion()).isEqualTo("Bearer token-de-moderacion");
        assertThat(correo.idempotencia()).isEqualTo("sancion-" + suspension.id() + "-emision");
        assertThat(mensaje.path("email").asString()).isEqualTo("jugador@nexus.test");
        assertThat(mensaje.path("apodo").asString()).isEqualTo("Jugador");
        assertThat(mensaje.path("tipo").asString()).isEqualTo("SUSPENSION");
        assertThat(mensaje.path("hasta").asString()).isNotBlank();
        assertThat(mensaje.path("apelableHasta").asString()).isNotBlank();

        assertThat(recibidas("POST", "/api/v1/internal/notifications")).hasSize(1);
        assertThat(salidas.findBySancionIdOrderByCreadoEnAsc(suspension.id()))
                .allSatisfy(s -> assertThat(s.entregadoEn()).isNotNull());
    }

    @Test
    @DisplayName("con identidad caida la sancion queda registrada y sus salidas esperan; al volver se entregan")
    void identidadCaida() throws Exception {
        estadoDeIdentidad = 503;
        UUID jugador = UUID.randomUUID();
        Sancion baneo = sanciones.emitir(admin, new SancionesService.SolicitudDeSancion(jugador, Sancion.Tipo.BANEO,
                "Fraude comprobado", null, null, null, true));

        entregador.ejecutar();

        assertThat(sanciones.activaDe(jugador)).as("la sancion se registro igual").isPresent();
        List<SalidaPendiente> pendientes = salidas.findBySancionIdOrderByCreadoEnAsc(baneo.id()).stream()
                .filter(s -> s.entregadoEn() == null).toList();
        assertThat(pendientes).extracting(SalidaPendiente::canal)
                .containsExactlyInAnyOrder(CanalDeSalida.PROYECCION, CanalDeSalida.CORREO);
        assertThat(pendientes).allSatisfy(s -> {
            assertThat(s.intentos()).isEqualTo(1);
            assertThat(s.ultimoError()).isNotBlank();
            assertThat(s.proximoIntentoEn()).isNotNull();
        });
        assertThat(recibidas("POST", "/api/v1/correos/sancion")).as("sin contacto no hay correo").isEmpty();

        // Identidad vuelve; se adelanta el reloj de la espera, que en esta
        // prueba es de una hora.
        estadoDeIdentidad = 200;
        jdbc.update("UPDATE moderacion_sanciones.avisos_pendientes SET proximo_intento_en = NULL WHERE sancion_id = ?",
                baneo.id());
        entregador.ejecutar();

        assertThat(salidas.findBySancionIdOrderByCreadoEnAsc(baneo.id()))
                .allSatisfy(s -> assertThat(s.entregadoEn()).isNotNull());
        JsonNode proyeccion = json.readTree(
                recibidas("PUT", "/api/v1/internal/usuarios/" + jugador + "/estado-sancion").get(0).cuerpo());
        assertThat(proyeccion.path("estado").asString()).isEqualTo("BANEADO");
        assertThat(recibidas("POST", "/api/v1/correos/sancion").get(0).idempotencia())
                .isEqualTo("sancion-" + baneo.id() + "-emision");
    }

    @Test
    @DisplayName("el levantamiento proyecta ACTIVO con el motivo y no manda correo")
    void levantamiento() throws Exception {
        UUID jugador = UUID.randomUUID();
        Sancion suspension = sanciones.emitir(admin, new SancionesService.SolicitudDeSancion(jugador,
                Sancion.Tipo.SUSPENSION, "Reincidencia", null, null, 24L, false));
        entregador.ejecutar();
        RECIBIDAS.clear();

        sanciones.levantar(new Actor(UUID.randomUUID(), "SUPER_ADMINISTRADOR"), suspension.id(), "Error de moderacion");
        entregador.ejecutar();

        JsonNode proyeccion = json.readTree(
                recibidas("PUT", "/api/v1/internal/usuarios/" + jugador + "/estado-sancion").get(0).cuerpo());
        assertThat(proyeccion.path("estado").asString()).isEqualTo("ACTIVO");
        assertThat(proyeccion.path("sancionId").asString()).isEqualTo(suspension.id().toString());
        assertThat(proyeccion.path("motivo").asString()).isEqualTo("Error de moderacion");
        assertThat(recibidas("POST", "/api/v1/correos/sancion")).isEmpty();
        assertThat(recibidas("POST", "/api/v1/internal/notifications")).hasSize(1);
    }

    @Test
    @DisplayName("una advertencia se notifica por correo (tipo ADVERTENCIA) sin proyectar nada")
    void advertencia() throws Exception {
        UUID jugador = UUID.randomUUID();
        sanciones.emitir(admin, new SancionesService.SolicitudDeSancion(jugador, Sancion.Tipo.ADVERTENCIA,
                "Primer aviso", null, null, null, false));

        entregador.ejecutar();

        assertThat(recibidas("PUT", "/estado-sancion")).isEmpty();
        JsonNode correo = json.readTree(recibidas("POST", "/api/v1/correos/sancion").get(0).cuerpo());
        assertThat(correo.path("tipo").asString()).isEqualTo("ADVERTENCIA");
        assertThat(correo.has("hasta")).isFalse();
    }

    // ------------------------------------------------------------------

    private static HttpServer levantarDobles() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            servidor.createContext("/api/v1/auth/token", intercambio -> responder(intercambio, 200,
                    "{\"access_token\":\"token-de-moderacion\",\"token_type\":\"Bearer\",\"expires_in\":300}"));
            servidor.createContext("/api/v1/internal/usuarios/", intercambio -> {
                anotar(intercambio);
                String ruta = intercambio.getRequestURI().getPath();
                if (estadoDeIdentidad != 200) {
                    responder(intercambio, estadoDeIdentidad, "{\"title\":\"no disponible\"}");
                } else if (ruta.endsWith("/contacto")) {
                    String uid = ruta.split("/")[5];
                    responder(intercambio, 200, "{\"uid\":\"" + uid + "\",\"email\":\"jugador@nexus.test\","
                            + "\"apodo\":\"Jugador\",\"estado\":\"ACTIVO\"}");
                } else {
                    responder(intercambio, 200, "{\"estado\":\"ok\",\"versionToken\":2}");
                }
            });
            servidor.createContext("/api/v1/correos/sancion", intercambio -> {
                anotar(intercambio);
                responder(intercambio, 202, "");
            });
            servidor.createContext("/api/v1/internal/notifications", intercambio -> {
                anotar(intercambio);
                responder(intercambio, 201, "{}");
            });
            servidor.start();
            return servidor;
        } catch (IOException e) {
            throw new IllegalStateException("no se pudieron levantar los dobles de identidad y correo", e);
        }
    }

    private static void anotar(HttpExchange intercambio) throws IOException {
        String cuerpo = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        RECIBIDAS.add(new Peticion(intercambio.getRequestMethod(), intercambio.getRequestURI().getPath(),
                intercambio.getRequestHeaders().getFirst("Authorization"),
                intercambio.getRequestHeaders().getFirst("Idempotency-Key"), cuerpo));
    }

    private static void responder(HttpExchange intercambio, int estado, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().add("Content-Type", "application/json");
        intercambio.sendResponseHeaders(estado, bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(bytes);
        }
    }
}
