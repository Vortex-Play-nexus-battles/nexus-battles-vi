package com.nexusbattles.ms_subastas.subastas.port;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbattles.comun.seguridad.servicio.CredencialDeServicioNoDisponible;
import com.nexusbattles.ms_subastas.seguridad.PortadorDeServicio;
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
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void credencialNoDisponibleImpideMutacion(boolean reversa) {
        var seguro = new FinanzasPublicacionClientHttp(
                URI.create("http://finanzas:8093/api/v1"), http, mapper, Duration.ofSeconds(2),
                PortadorDeServicio.de(() -> {
                    throw new CredencialDeServicioNoDisponible("emisor caido", new IOException("down"));
                }));
        var error = assertThrows(FinanzasPublicacionClientException.class, () -> {
            if (reversa) seguro.compensarDebito(subasta, "publicacion-fallida");
            else seguro.debitarComision(uid, BigDecimal.ONE, subasta, "comision-publicacion-24h");
        });
        assertEquals(com.nexusbattles.ms_subastas.subastas.service.PublicacionSubastaException.Motivo.DEPENDENCIA_NO_DISPONIBLE,
                error.getMotivo());
        assertFalse(error.resultadoIncierto());
        verifyNoInteractions(http);
    }

    @Test
    void credencialCaidaTrasTimeoutImpideConsultaYConservaIncertidumbre() throws Exception {
        var llamadas = new java.util.concurrent.atomic.AtomicInteger();
        var seguro = new FinanzasPublicacionClientHttp(
                URI.create("http://finanzas:8093/api/v1"), http, mapper, Duration.ofSeconds(2),
                PortadorDeServicio.de(() -> {
                    if (llamadas.incrementAndGet() == 1) return "token-servicio-prueba";
                    throw new CredencialDeServicioNoDisponible("emisor caido", new IOException("down"));
                }));
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new java.net.http.HttpTimeoutException("resultado perdido"));
        var error = assertThrows(FinanzasPublicacionClientException.class,
                () -> seguro.debitarComision(uid, BigDecimal.ONE, subasta, "comision-publicacion-24h"));
        assertTrue(error.resultadoIncierto());
        assertEquals(2, llamadas.get());
        solicitud("debitar");
    }

    @ParameterizedTest
    @ValueSource(strings = {"otro-error", "reserva-no-encontrada", "reserva-ya-liberada"})
    void otro422EsTecnico(String tipo) throws Exception {
        responder(422, "{\"type\":\"https://nexusbattles.upb.edu.co/errors/" + tipo + "\"}");
        var error = assertThrows(FinanzasPublicacionClientException.class, () -> ejecutar(false));
        assertEquals(com.nexusbattles.ms_subastas.subastas.service.PublicacionSubastaException.Motivo.DEPENDENCIA_NO_DISPONIBLE,
                error.getMotivo());
        solicitud("debitar");
    }

    @Test
    void repetirDebitoConservaRefIdDelProveedor() throws Exception {
        responder(200, "{\"refId\":\"" + refId + "\",\"estado\":\"EXITOSO\"}");
        ejecutar(false);
        ejecutar(false);
        var captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(2)).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        for (var request : captor.getAllValues()) {
            assertEquals(refId, mapper.readTree(body(request)).get("refId").asText());
        }
        assertEquals(body(captor.getAllValues().get(0)), body(captor.getAllValues().get(1)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"REVERSADO", "YA_REVERSADO"})
    void aceptaReversaIdempotente(String estado) throws Exception {
        responder(200, "{\"refId\":\"" + refId + "\",\"estado\":\"" + estado + "\"}");
        ejecutar(true);
        solicitud("reversar");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void timeoutResueltoPorConsultaNoRepiteMutacion(boolean reversa) throws Exception {
        prepararConsulta(200, operacion(reversa ? "LIBERADA" : "CONSUMIDA"));
        ejecutar(reversa);
        verificarMutacionYConsulta(reversa);
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 409, 500})
    void consultaFallidaNoPruebaAusenciaDeDebito(int status) throws Exception {
        prepararConsulta(status, "{}");
        assertTrue(assertThrows(FinanzasPublicacionClientException.class, () -> ejecutar(false)).resultadoIncierto());
        verificarMutacionYConsulta(false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVA", "LIBERADA", "UNKNOWN"})
    void consultaSinDebitoAplicadoEsIncierta(String estado) throws Exception {
        prepararConsulta(200, operacion(estado));
        assertTrue(assertThrows(FinanzasPublicacionClientException.class, () -> ejecutar(false)).resultadoIncierto());
        verificarMutacionYConsulta(false);
    }

    @Test
    void consultaConDatosAjenosNoConfirmaDebito() throws Exception {
        prepararConsulta(200, operacion("CONSUMIDA").replace(uid.toString(), UUID.randomUUID().toString()));
        assertTrue(assertThrows(FinanzasPublicacionClientException.class, () -> ejecutar(false)).resultadoIncierto());
    }

    @Test
    void debitoConsumidoNoConfirmaReversa() throws Exception {
        prepararConsulta(200, operacion("CONSUMIDA"));
        assertTrue(assertThrows(FinanzasPublicacionClientException.class, () -> ejecutar(true)).resultadoIncierto());
        verificarMutacionYConsulta(true);
    }

    private String operacion(String estado) {
        return "{\"refId\":\"" + refId + "\",\"uid\":\"" + uid
                + "\",\"monto\":1,\"concepto\":\"comision-publicacion-24h\",\"estado\":\"" + estado + "\"}";
    }

    private void prepararConsulta(int status, String body) throws Exception {
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new java.net.http.HttpTimeoutException("resultado perdido")).thenReturn(response);
    }

    private void verificarMutacionYConsulta(boolean reversa) throws Exception {
        var captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(2)).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        var requests = captor.getAllValues();
        assertEquals("POST", requests.get(0).method());
        assertTrue(requests.get(0).uri().getPath().endsWith(reversa ? "/reversar" : "/debitar"));
        assertEquals("GET", requests.get(1).method());
        assertEquals("/api/v1/creditos/operaciones/" + refId, requests.get(1).uri().getPath());
        assertEquals(Duration.ofSeconds(2), requests.get(1).timeout().orElseThrow());
        for (var request : requests) {
            assertEquals("Bearer token-servicio-prueba", request.headers().firstValue("Authorization").orElseThrow());
        }
    }

    @Test
    void saldoInsuficiente422EsRechazoSinSegundoDebito() throws Exception {
        responder(422, "{\"type\":\"https://nexusbattles.upb.edu.co/errors/saldo-insuficiente\","
                + "\"title\":\"Saldo insuficiente\",\"status\":422}");
        var error = assertThrows(com.nexusbattles.ms_subastas.subastas.service.PublicacionSubastaException.class,
                () -> ejecutar(false));
        assertEquals(com.nexusbattles.ms_subastas.subastas.service.PublicacionSubastaException.Motivo.REGLA_NEGOCIO,
                error.getMotivo());
        solicitud("debitar");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void timeoutConsultaUnaVezYConservaResultadoIncierto(boolean reversa) throws Exception {
        var causa = new java.net.http.HttpTimeoutException("sin respuesta");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(causa);
        assertSame(causa, assertThrows(FinanzasPublicacionClientException.class, () -> ejecutar(reversa)).getCause());
        verify(http, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }
    private final HttpClient http = mock(HttpClient.class);
    private final HttpResponse<String> response = mock(HttpResponse.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final UUID uid = UUID.randomUUID(), subasta = UUID.randomUUID();
    private final String refId = "sub-publicacion-" + subasta;
    private final FinanzasPublicacionClientHttp client = new FinanzasPublicacionClientHttp(
            URI.create("http://finanzas:8093/api/v1"), http, mapper, Duration.ofSeconds(2),
            PortadorDeServicio.de(() -> "token-servicio-prueba"));

    @ParameterizedTest
    @ValueSource(ints = {24, 48})
    void debito200EnviaContratoReal(int horas) throws Exception {
        int monto = horas == 24 ? 1 : 3;
        responder(200, "{\"refId\":\"" + refId + "\",\"transaccionId\":\"tx\",\"estado\":\"EXITOSO\","
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
    @ValueSource(ints = {201, 204, 400, 401, 403, 404, 409, 422, 500, 503})
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
        responder(200, "{\"refId\":\"" + refId + "\",\"estado\":\"YA_REVERSADO\"}");
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
