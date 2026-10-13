package com.nexusbattles.ms_subastas.subastas.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbattles.ms_subastas.seguridad.PortadorDeServicio;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * G7 — la penalizacion de cancelar contra el libro de creditos. ms-finanzas no
 * vuelve a descontar un {@code refId} ya registrado, tampoco si se reverso
 * (creditos.yaml 1.4.1): por eso una penalizacion devuelta abre otra
 * generacion del refId y nunca se reutiliza.
 */
class PenalizacionDeCancelacionTest {

    private final HttpClient http = mock(HttpClient.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final UUID vendedor = UUID.randomUUID();
    private final UUID subasta = UUID.randomUUID();
    private final String base = "sub-cancelacion-" + subasta;
    /** El libro falso: estado de cada refId. */
    private final Map<String, String> libro = new HashMap<>();
    /** Lo que se mando, en orden: «GET refId», «POST debitar refId», «POST reversar refId». */
    private final List<String> enviadas = new ArrayList<>();
    private Integer consultaFallaCon;

    private final FinanzasPublicacionClientHttp cliente = new FinanzasPublicacionClientHttp(
            URI.create("http://finanzas:8093/api/v1"), http, mapper, Duration.ofSeconds(2),
            PortadorDeServicio.de(() -> "token-servicio-prueba"));

    @BeforeEach
    @SuppressWarnings("unchecked")
    void libroFalso() throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(invocacion -> {
            HttpRequest peticion = invocacion.getArgument(0);
            String ruta = peticion.uri().getPath();
            if ("GET".equals(peticion.method())) {
                String refId = ruta.substring(ruta.lastIndexOf('/') + 1);
                enviadas.add("GET " + refId);
                if (consultaFallaCon != null) {
                    return respuesta(consultaFallaCon, "{}");
                }
                String estado = libro.get(refId);
                return estado == null ? respuesta(404, "{}")
                        : respuesta(200, "{\"refId\":\"" + refId + "\",\"uid\":\"" + vendedor
                                + "\",\"monto\":1.5,\"concepto\":\"penalizacion-cancelacion-subasta\",\"estado\":\""
                                + estado + "\"}");
            }
            String refId = mapper.readTree(cuerpo(peticion)).get("refId").asText();
            String operacion = ruta.substring(ruta.lastIndexOf('/') + 1);
            enviadas.add("POST " + operacion + " " + refId);
            if (operacion.equals("debitar")) {
                libro.putIfAbsent(refId, "CONSUMIDA");
            } else if (libro.containsKey(refId)) {
                libro.put(refId, "LIBERADA");
            }
            return respuesta(200, "{\"refId\":\"" + refId + "\",\"estado\":\"OK\"}");
        });
    }

    @Test
    @DisplayName("la primera penalizacion usa el refId de siempre")
    void primera() {
        cliente.debitarPenalizacionCancelacion(vendedor, new BigDecimal("1.50"), subasta);

        assertEquals(List.of("GET " + base, "POST debitar " + base), enviadas);
    }

    @Test
    @DisplayName("tras una penalizacion devuelta, la siguiente se cobra con otro refId: el devuelto no se reutiliza")
    void devueltaNoSeReutiliza() {
        libro.put(base, "LIBERADA");

        cliente.debitarPenalizacionCancelacion(vendedor, new BigDecimal("1.50"), subasta);

        assertEquals(List.of("GET " + base, "GET " + base + "-2", "POST debitar " + base + "-2"), enviadas);
        assertEquals("CONSUMIDA", libro.get(base + "-2"));
    }

    @Test
    @DisplayName("una penalizacion cobrada cuya respuesta se perdio se repite con su refId: no cobra dos veces")
    void cobradaSeRepiteConElMismo() {
        libro.put(base, "LIBERADA");
        libro.put(base + "-2", "CONSUMIDA");

        cliente.debitarPenalizacionCancelacion(vendedor, new BigDecimal("1.50"), subasta);

        assertEquals("POST debitar " + base + "-2", enviadas.getLast());
        assertEquals(2, libro.size(), "ninguna generacion nueva");
    }

    @Test
    @DisplayName("devolver reversa la ultima cobrada, no la primera")
    void devolverLaUltima() {
        libro.put(base, "LIBERADA");
        libro.put(base + "-2", "CONSUMIDA");

        cliente.compensarPenalizacionCancelacion(subasta, "cancelacion-fallida");

        assertEquals("POST reversar " + base + "-2", enviadas.getLast());
        assertEquals("LIBERADA", libro.get(base + "-2"));
    }

    @Test
    @DisplayName("el ciclo completo: cobrar, devolver, volver a cobrar y devolver otra vez, siempre la que toca")
    void cicloCompleto() {
        cliente.debitarPenalizacionCancelacion(vendedor, new BigDecimal("1.50"), subasta);
        cliente.compensarPenalizacionCancelacion(subasta, "cancelacion-fallida");
        cliente.debitarPenalizacionCancelacion(vendedor, new BigDecimal("1.50"), subasta);
        cliente.compensarPenalizacionCancelacion(subasta, "cancelacion-fallida");
        cliente.debitarPenalizacionCancelacion(vendedor, new BigDecimal("1.50"), subasta);

        assertEquals(Map.of(base, "LIBERADA", base + "-2", "LIBERADA", base + "-3", "CONSUMIDA"), libro);
        assertEquals(3, enviadas.stream().filter(e -> e.startsWith("POST debitar")).count());
    }

    @Test
    @DisplayName("si el libro no contesta la consulta no se cobra a ciegas")
    void consultaFallidaNoCobra() {
        consultaFallaCon = 503;

        assertThrows(FinanzasPublicacionClientException.class,
                () -> cliente.debitarPenalizacionCancelacion(vendedor, new BigDecimal("1.50"), subasta));
        assertTrue(enviadas.stream().noneMatch(e -> e.startsWith("POST")));
    }

    @Test
    @DisplayName("con demasiadas penalizaciones devueltas se para: eso lo concilia una persona")
    void tope() {
        for (int g = 1; g <= FinanzasPublicacionClientHttp.GENERACIONES_MAXIMAS; g++) {
            libro.put(FinanzasPublicacionClientHttp.refIdDeLaPenalizacion(subasta, g), "LIBERADA");
        }

        assertThrows(FinanzasPublicacionClientException.class,
                () -> cliente.debitarPenalizacionCancelacion(vendedor, new BigDecimal("1.50"), subasta));
        assertTrue(enviadas.stream().noneMatch(e -> e.startsWith("POST")));
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> respuesta(int estado, String cuerpo) {
        HttpResponse<String> respuesta = mock(HttpResponse.class);
        when(respuesta.statusCode()).thenReturn(estado);
        when(respuesta.body()).thenReturn(cuerpo);
        return respuesta;
    }

    private static String cuerpo(HttpRequest request) throws Exception {
        var bytes = new java.io.ByteArrayOutputStream();
        var completado = new CompletableFuture<String>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer buffer) {
                byte[] fragmento = new byte[buffer.remaining()];
                buffer.get(fragmento);
                bytes.writeBytes(fragmento);
            }
            public void onError(Throwable error) { completado.completeExceptionally(error); }
            public void onComplete() { completado.complete(bytes.toString(StandardCharsets.UTF_8)); }
        });
        return completado.get(2, TimeUnit.SECONDS);
    }
}
