package com.nexusbattles.ms_subastas.subastas.port;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Flow;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FinanzasPublicacionClientHttpTest {
    private final HttpClient http = mock(HttpClient.class);
    private final HttpResponse<String> response = mock(HttpResponse.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final UUID uid = UUID.randomUUID(), subasta = UUID.randomUUID();
    private final String refId = "sub-publicacion-" + subasta;
    private final FinanzasPublicacionClientHttp client = new FinanzasPublicacionClientHttp(
            URI.create("http://finanzas:8093/api/v1"), http, mapper, Duration.ofSeconds(2));

    @ParameterizedTest
    @ValueSource(ints = {24, 48})
    void debito200EnviaContratoReal(int horas) throws Exception {
        int monto = horas == 24 ? 1 : 3;
        responder(200, "{\"refId\":\"" + refId + "\",\"transaccionId\":\"tx\",\"estado\":\"DEBITADO\","
                + "\"montoDebitado\":" + monto + ",\"nuevoSaldoDisponible\":99}");
        client.debitarComision(uid, BigDecimal.valueOf(monto), subasta, "comision-publicacion-" + horas + "h");
        var request = solicitud("debitar");
        var json = mapper.readTree(body(request));
        assertEquals(4, json.size());
        assertEquals(uid.toString(), json.get("uid").asText());
        assertEquals(refId, json.get("refId").asText());
        assertTrue(json.get("monto").isNumber());
        assertEquals(0, BigDecimal.valueOf(monto).compareTo(json.get("monto").decimalValue()));
        assertEquals("comision-publicacion-" + horas + "h", json.get("concepto").asText());
    }

    @Test
    void reversa200EnviaSoloReferenciaYMotivo() throws Exception {
        responder(200, "{\"refId\":\"" + refId + "\",\"estado\":\"REVERSADO\","
                + "\"montoReversado\":1,\"motivo\":\"publicacion-fallida\"}");
        client.compensarDebito(subasta, "publicacion-fallida");
        var json = mapper.readTree(body(solicitud("reversar")));
        assertEquals(2, json.size());
        assertEquals(refId, json.get("refId").asText());
        assertEquals("publicacion-fallida", json.get("motivo").asText());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rechazaOtraReferencia(boolean reversa) throws Exception {
        responder(200, "{\"refId\":\"sub-publicacion-" + UUID.randomUUID() + "\"}");
        assertThrows(FinanzasPublicacionClientException.class, () -> ejecutar(reversa));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{malformed", "{}", "null", "", "{\"refId\":null}"})
    void rechazaRespuestaInvalidaOIncompleta(String json) throws Exception {
        responder(200, json);
        assertThrows(FinanzasPublicacionClientException.class, () -> ejecutar(false));
        assertThrows(FinanzasPublicacionClientException.class, () -> ejecutar(true));
    }

    @ParameterizedTest
    @ValueSource(ints = {201, 204, 400, 404, 409, 500})
    void rechazaStatusDistintoDe200(int status) throws Exception {
        responder(status, "{\"refId\":\"" + refId + "\"}");
        assertThrows(FinanzasPublicacionClientException.class, () -> ejecutar(false));
        assertThrows(FinanzasPublicacionClientException.class, () -> ejecutar(true));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void exponeIOException(boolean reversa) throws Exception {
        var causa = new IOException("down");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(causa);
        assertSame(causa, assertThrows(FinanzasPublicacionClientException.class, () -> ejecutar(reversa)).getCause());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void restauraInterrupcion(boolean reversa) throws Exception {
        var causa = new InterruptedException("interrumpido");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(causa);
        try {
            assertSame(causa, assertThrows(FinanzasPublicacionClientException.class, () -> ejecutar(reversa)).getCause());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }

    @Test
    void validaEntradasAntesDeEnviar() {
        assertThrows(FinanzasPublicacionClientException.class, () -> client.debitarComision(null, BigDecimal.ONE, subasta, "concepto"));
        for (BigDecimal monto : new BigDecimal[] {null, BigDecimal.ZERO, BigDecimal.ONE.negate()}) {
            assertThrows(FinanzasPublicacionClientException.class, () -> client.debitarComision(uid, monto, subasta, "concepto"));
        }
        assertThrows(FinanzasPublicacionClientException.class, () -> client.debitarComision(uid, BigDecimal.ONE, null, "concepto"));
        assertThrows(FinanzasPublicacionClientException.class, () -> client.compensarDebito(null, "motivo"));
        for (String texto : new String[] {null, "", " "}) {
            assertThrows(FinanzasPublicacionClientException.class, () -> client.debitarComision(uid, BigDecimal.ONE, subasta, texto));
            assertThrows(FinanzasPublicacionClientException.class, () -> client.compensarDebito(subasta, texto));
        }
        verifyNoInteractions(http);
    }

    @Test
    void aceptaBaseConBarraFinal() throws Exception {
        responder(200, "{\"refId\":\"" + refId + "\"}");
        new FinanzasPublicacionClientHttp(URI.create("http://finanzas:8093/api/v1/"), http, mapper, Duration.ofSeconds(2))
                .compensarDebito(subasta, "publicacion-fallida");
        solicitud("reversar");
    }

    private void ejecutar(boolean reversa) {
        if (reversa) client.compensarDebito(subasta, "publicacion-fallida");
        else client.debitarComision(uid, BigDecimal.ONE, subasta, "comision-publicacion-24h");
    }

    private void responder(int status, String body) throws Exception {
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
    }

    private HttpRequest solicitud(String operacion) throws Exception {
        var captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        var request = captor.getValue();
        assertEquals(URI.create("http://finanzas:8093/api/v1/creditos/" + operacion), request.uri());
        assertEquals("POST", request.method());
        assertEquals(Duration.ofSeconds(2), request.timeout().orElseThrow());
        assertEquals("application/json", request.headers().firstValue("Content-Type").orElseThrow());
        assertEquals("application/json", request.headers().firstValue("Accept").orElseThrow());
        assertTrue(request.headers().firstValue("Idempotency-Key").isEmpty());
        return request;
    }

    private static String body(HttpRequest request) throws Exception {
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
