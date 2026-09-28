package com.nexusbattles.ms_subastas.notificaciones;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbattles.comun.observabilidad.FiltroDeTraza;
import com.nexusbattles.comun.seguridad.servicio.CredencialDeServicioNoDisponible;
import com.nexusbattles.comun.seguridad.servicio.TokenDeServicio;
import com.nexusbattles.ms_subastas.seguridad.PortadorDeServicio;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Los dos adaptadores HTTP de la salida por correo (B8) contra un servidor
 * HTTP de verdad: {@code POST /api/v1/correos/subasta} ({@code correo.yaml})
 * y {@code GET /api/v1/internal/usuarios/{uid}/contacto}
 * ({@code ms-identidad-admin.yaml}). Lo que importa: credencial de servicio,
 * {@code Idempotency-Key} estable, traza, y que cada respuesta se clasifique
 * bien entre «reintentar» y «no insistir».
 */
@DisplayName("Adaptadores HTTP de correo y contacto")
class CorreoYContactoHttpTest {

    private static final TokenDeServicio TOKEN = () -> "token-de-ms-subastas";

    private HttpServer servidor;
    private final AtomicInteger estado = new AtomicInteger(202);
    private final AtomicReference<String> cuerpoDeRespuesta = new AtomicReference<>("");
    private final AtomicReference<HttpExchange> ultima = new AtomicReference<>();
    private final AtomicReference<String> cuerpoRecibido = new AtomicReference<>();
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void levantar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/", intercambio -> {
            ultima.set(intercambio);
            cuerpoRecibido.set(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] cuerpo = cuerpoDeRespuesta.get().getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().set("Content-Type", "application/json");
            intercambio.sendResponseHeaders(estado.get(), cuerpo.length == 0 ? -1 : cuerpo.length);
            if (cuerpo.length > 0) {
                intercambio.getResponseBody().write(cuerpo);
            }
            intercambio.close();
        });
        servidor.start();
    }

    @AfterEach
    void apagar() {
        servidor.stop(0);
        MDC.remove(FiltroDeTraza.CLAVE_MDC);
    }

    private String base() {
        return "http://localhost:" + servidor.getAddress().getPort() + "/";
    }

    private static HttpClient http() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    @Nested
    @DisplayName("POST /correos/subasta")
    class Correo {

        private CorreoSubastaClientHttp cliente(PortadorDeServicio portador) {
            return new CorreoSubastaClientHttp(base(), http(), mapper, Duration.ofSeconds(2), portador);
        }

        private final CorreoSubastaClient.CorreoSubasta correo = new CorreoSubastaClient.CorreoSubasta(
                "lyra@example.com", "lyra", "Te superaron en una subasta", "Otra puja supero tu oferta.", true);

        @Test
        @DisplayName("202: sale con credencial, clave de idempotencia, traza y el cuerpo del contrato")
        void enviaConCredencialClaveYTraza() throws Exception {
            MDC.put(FiltroDeTraza.CLAVE_MDC, "0af7651916cd43dd8448eb211c80319c");

            cliente(PortadorDeServicio.de(TOKEN)).enviar("ms-subastas-clave-1", correo);

            HttpExchange peticion = ultima.get();
            assertEquals("POST", peticion.getRequestMethod());
            assertEquals("/api/v1/correos/subasta", peticion.getRequestURI().getPath());
            assertEquals("Bearer token-de-ms-subastas", peticion.getRequestHeaders().getFirst("Authorization"));
            assertEquals("ms-subastas-clave-1", peticion.getRequestHeaders().getFirst("Idempotency-Key"));
            assertTrue(peticion.getRequestHeaders().getFirst("traceparent")
                    .startsWith("00-0af7651916cd43dd8448eb211c80319c-"));
            JsonNode cuerpo = mapper.readTree(cuerpoRecibido.get());
            assertEquals("lyra@example.com", cuerpo.path("email").asText());
            assertEquals("lyra", cuerpo.path("apodo").asText());
            assertEquals("Te superaron en una subasta", cuerpo.path("asunto").asText());
            assertEquals("Otra puja supero tu oferta.", cuerpo.path("mensaje").asText());
            assertTrue(cuerpo.path("debeEnviarCorreo").asBoolean());
        }

        @Test
        @DisplayName("sin traza en curso no inventa cabecera; sin credencial, sin Authorization")
        void sinTrazaNiCredencial() {
            cliente(PortadorDeServicio.ninguno()).enviar("k", correo);

            assertNull(ultima.get().getRequestHeaders().getFirst("traceparent"));
            assertNull(ultima.get().getRequestHeaders().getFirst("Authorization"));
        }

        @ParameterizedTest
        @ValueSource(ints = {400, 422})
        @DisplayName("400/422: el aviso esta mal formado y reintentar no lo arregla")
        void rechazoDefinitivo(int codigo) {
            estado.set(codigo);

            assertThrows(CorreoRechazadoException.class,
                    () -> cliente(PortadorDeServicio.de(TOKEN)).enviar("k", correo));
        }

        @ParameterizedTest
        @ValueSource(ints = {401, 403, 429, 500, 503})
        @DisplayName("credencial rechazada o averia: se reintenta mas tarde")
        void noDisponible(int codigo) {
            estado.set(codigo);

            assertThrows(CorreoNoDisponibleException.class,
                    () -> cliente(PortadorDeServicio.de(TOKEN)).enviar("k", correo));
        }

        @Test
        @DisplayName("sin servidor: no disponible")
        void sinServidor() {
            servidor.stop(0);

            assertThrows(CorreoNoDisponibleException.class,
                    () -> cliente(PortadorDeServicio.ninguno()).enviar("k", correo));
        }

        @Test
        @DisplayName("si el emisor no entrega la credencial: no disponible, sin llamar")
        void credencialNoDisponible() {
            TokenDeServicio roto = () -> {
                throw new CredencialDeServicioNoDisponible("sin token", null);
            };

            assertThrows(CorreoNoDisponibleException.class,
                    () -> cliente(PortadorDeServicio.de(roto)).enviar("k", correo));
            assertNull(ultima.get(), "no debe salir ninguna peticion");
        }

        @Test
        @DisplayName("la clave de idempotencia es obligatoria")
        void claveObligatoria() {
            assertThrows(NullPointerException.class,
                    () -> cliente(PortadorDeServicio.ninguno()).enviar(null, correo));
        }
    }

    @Nested
    @DisplayName("GET /internal/usuarios/{uid}/contacto")
    class Contacto {

        private ContactoClientHttp cliente(PortadorDeServicio portador) {
            return new ContactoClientHttp(base(), http(), mapper, Duration.ofSeconds(2), portador);
        }

        @Test
        @DisplayName("200: el correo, el apodo y el estado, pedidos con credencial de servicio")
        void leeElContacto() {
            UUID uid = UUID.randomUUID();
            estado.set(200);
            cuerpoDeRespuesta.set("{\"uid\":\"" + uid + "\",\"email\":\"lyra@example.com\",\"apodo\":\"lyra\","
                    + "\"estado\":\"ACTIVO\",\"otroCampo\":1}");

            Optional<ContactoClient.Contacto> contacto = cliente(PortadorDeServicio.de(TOKEN)).contactoDe(uid);

            assertEquals(new ContactoClient.Contacto(uid, "lyra@example.com", "lyra", "ACTIVO"), contacto.orElseThrow());
            assertEquals("/api/v1/internal/usuarios/" + uid + "/contacto", ultima.get().getRequestURI().getPath());
            assertEquals("GET", ultima.get().getRequestMethod());
            assertEquals("Bearer token-de-ms-subastas", ultima.get().getRequestHeaders().getFirst("Authorization"));
        }

        /**
         * La ruta figura como pendiente en ms-identidad-admin.yaml (B2): un 404
         * puede ser «no existe la ruta», y descartar por eso tiraria todos los
         * correos en silencio. Se reintenta.
         */
        @Test
        @DisplayName("404: no se descarta el correo, se reintenta (la ruta puede no estar desplegada)")
        void cuatroCeroCuatroSeReintenta() {
            estado.set(404);

            CorreoNoDisponibleException error = assertThrows(CorreoNoDisponibleException.class,
                    () -> cliente(PortadorDeServicio.ninguno()).contactoDe(UUID.randomUUID()));
            assertTrue(error.getMessage().contains("404"));
        }

        @ParameterizedTest
        @ValueSource(ints = {401, 403, 500, 503})
        @DisplayName("credencial rechazada o averia: se reintenta")
        void noDisponible(int codigo) {
            estado.set(codigo);

            assertThrows(CorreoNoDisponibleException.class,
                    () -> cliente(PortadorDeServicio.ninguno()).contactoDe(UUID.randomUUID()));
        }

        @Test
        @DisplayName("un cuerpo ilegible tampoco se da por bueno")
        void cuerpoIlegible() {
            estado.set(200);
            cuerpoDeRespuesta.set("{no es json");

            assertThrows(CorreoNoDisponibleException.class,
                    () -> cliente(PortadorDeServicio.ninguno()).contactoDe(UUID.randomUUID()));
        }

        @Test
        @DisplayName("sin servidor o sin credencial: no disponible")
        void sinServidorOSinCredencial() {
            TokenDeServicio roto = () -> {
                throw new CredencialDeServicioNoDisponible("sin token", null);
            };
            assertThrows(CorreoNoDisponibleException.class,
                    () -> cliente(PortadorDeServicio.de(roto)).contactoDe(UUID.randomUUID()));

            servidor.stop(0);
            assertThrows(CorreoNoDisponibleException.class,
                    () -> cliente(PortadorDeServicio.ninguno()).contactoDe(UUID.randomUUID()));
        }
    }
}
