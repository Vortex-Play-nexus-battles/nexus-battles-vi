package com.nexusbattles.ms_subastas.pujas.creditos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/**
 * Adaptador HTTP hacia ms-finanzas, contra la API que publica hoy
 * {@code CreditoController}:
 *
 * <pre>
 *   POST /creditos/reservar                       + Idempotency-Key
 *   POST /creditos/reservas/{reservaId}/liberar
 *   POST /creditos/reservas/{reservaId}/consumir  {vendedorUid}
 *   GET  /creditos/{uid}/saldo
 * </pre>
 *
 * <p><b>Los nombres de campo son los suyos, no los de nuestro dominio.</b> El
 * jugador viaja como {@code jugadorUid} y la subasta como {@code referenciaId};
 * traducirlo aqui es justamente lo que evita que su nomenclatura se filtre al
 * motor de pujas.
 *
 * <p><b>Limitacion conocida, y es la razon de que el modo por defecto siga
 * siendo el doble:</b> ms-finanzas no tiene manejador de errores, asi que
 * {@code SaldoInsuficienteException} y {@code ReservaNoEncontradaException}
 * salen como <b>500</b>, iguales que una averia real. Desde aqui no hay forma
 * fiable de distinguir "no tienes creditos" —que el jugador debe ver y que no
 * se debe reintentar— de "el servicio se cayo". Se traduce el 500 como averia,
 * que es lo unico prudente: reintentar y cortar ante un fallo del servicio es
 * recuperable, mientras que tratar una caida como saldo insuficiente le diria
 * al jugador algo falso sobre su dinero. Pedido a Juan Diego mapear esas dos
 * excepciones a 409 y 404, como decia su propio borrador de contrato.
 */
public class CreditoClientHttp implements CreditoClient {

    private final URI baseUri;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Duration timeout;

    public CreditoClientHttp(String baseUrl, long timeoutMs, ObjectMapper objectMapper) {
        this(URI.create(baseUrl),
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build(),
                objectMapper, Duration.ofMillis(timeoutMs));
    }

    public CreditoClientHttp(URI baseUri, HttpClient httpClient, ObjectMapper objectMapper, Duration timeout) {
        this.baseUri = baseUri;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.timeout = timeout;
    }

    @Override
    public ReservaCredito reservar(UUID jugadorId, BigDecimal monto, UUID subastaId, String idempotencyKey) {
        exigir(jugadorId != null, "El jugador es obligatorio para reservar");
        exigir(monto != null && monto.signum() > 0, "El monto a reservar debe ser positivo");
        exigir(subastaId != null, "La subasta es obligatoria para reservar");
        // ms-finanzas la declara obligatoria: sin ella responde 400.
        exigir(idempotencyKey != null && !idempotencyKey.isBlank(),
                "La clave de idempotencia es obligatoria para reservar creditos");

        HttpRequest peticion = HttpRequest.newBuilder(uri("/creditos/reservar"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(serializar(
                        new ReservarRequest(jugadorId.toString(), monto,
                                "Puja en la subasta " + subastaId, subastaId.toString()))))
                .build();

        HttpResponse<String> respuesta = enviar(peticion, "reservar creditos");
        exigirExito(respuesta, "reservar creditos");

        ReservaJson json = leer(respuesta.body(), ReservaJson.class, "reservar creditos");
        exigir(json.reservaId() != null, "ms-finanzas no devolvio el identificador de la reserva");
        return new ReservaCredito(UUID.fromString(json.reservaId()), jugadorId,
                json.monto() == null ? monto : json.monto(),
                ReservaCredito.EstadoReserva.RESERVADA);
    }

    @Override
    public void liberar(UUID reservaId) {
        exigir(reservaId != null, "La reserva es obligatoria para liberar");

        HttpRequest peticion = HttpRequest.newBuilder(uri("/creditos/reservas/" + reservaId + "/liberar"))
                .header("Accept", "application/json")
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        exigirExito(enviar(peticion, "liberar la reserva"), "liberar la reserva");
    }

    @Override
    public void consumir(UUID reservaId, UUID vendedorId) {
        exigir(reservaId != null, "La reserva es obligatoria para consumir");

        // vendedorUid puede ir nulo y ms-finanzas lo admite: entonces cobra al
        // comprador sin abonar a nadie. Aqui no se deja pasar, porque en una
        // subasta siempre hay a quien pagarle y hacerlo en silencio seria
        // quedarse los creditos por el camino.
        exigir(vendedorId != null, "El vendedor es obligatorio: sin el, el comprador paga y nadie cobra");

        HttpRequest peticion = HttpRequest.newBuilder(uri("/creditos/reservas/" + reservaId + "/consumir"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(
                        serializar(new ConsumirRequest(vendedorId.toString()))))
                .build();

        exigirExito(enviar(peticion, "consumir la reserva"), "consumir la reserva");
    }

    @Override
    public BigDecimal saldoDisponible(UUID jugadorId) {
        exigir(jugadorId != null, "El jugador es obligatorio para consultar el saldo");

        HttpRequest peticion = HttpRequest.newBuilder(uri("/creditos/" + jugadorId + "/saldo"))
                .header("Accept", "application/json")
                .timeout(timeout)
                .GET()
                .build();

        HttpResponse<String> respuesta = enviar(peticion, "consultar el saldo");
        exigirExito(respuesta, "consultar el saldo");

        SaldoJson json = leer(respuesta.body(), SaldoJson.class, "consultar el saldo");
        exigir(json.saldoDisponible() != null, "ms-finanzas no devolvio el saldo disponible");
        // Su "saldoDisponible" ya viene neto de reservas, que es justo lo que
        // este servicio necesita: el bruto permitiria comprometerse dos veces
        // con los mismos creditos en pujas simultaneas.
        return json.saldoDisponible();
    }

    private void exigirExito(HttpResponse<String> respuesta, String queSeIntentaba) {
        int estado = respuesta.statusCode();
        if (estado >= 200 && estado < 300) {
            return;
        }
        if (estado >= 500) {
            throw new CreditoNoDisponibleException(
                    "ms-finanzas respondio " + estado + " al " + queSeIntentaba
                            + ". Mientras no distinga sus rechazos de negocio del fallo del servidor, "
                            + "un saldo insuficiente llega tambien como 500 y no se puede separar aqui.");
        }
        throw new CreditoClientException(CreditoClientException.Motivo.RESPUESTA_INESPERADA,
                "ms-finanzas respondio " + estado + " al " + queSeIntentaba);
    }

    private HttpResponse<String> enviar(HttpRequest peticion, String queSeIntentaba) {
        try {
            return httpClient.send(peticion, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new CreditoNoDisponibleException("No se pudo contactar a ms-finanzas al " + queSeIntentaba, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CreditoNoDisponibleException("Peticion a ms-finanzas interrumpida al " + queSeIntentaba, e);
        }
    }

    private String serializar(Object cuerpo) {
        try {
            return objectMapper.writeValueAsString(cuerpo);
        } catch (IOException e) {
            throw new CreditoClientException(CreditoClientException.Motivo.RESPUESTA_INESPERADA,
                    "No se pudo serializar la peticion para ms-finanzas", e);
        }
    }

    private <T> T leer(String cuerpo, Class<T> tipo, String queSeIntentaba) {
        try {
            return objectMapper.readValue(cuerpo, tipo);
        } catch (IOException e) {
            throw new CreditoClientException(CreditoClientException.Motivo.RESPUESTA_INESPERADA,
                    "Respuesta de ms-finanzas ilegible al " + queSeIntentaba, e);
        }
    }

    private static void exigir(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new CreditoClientException(CreditoClientException.Motivo.RESPUESTA_INESPERADA, mensaje);
        }
    }

    private URI uri(String ruta) {
        String base = baseUri.toString().replaceAll("/+$", "");
        return URI.create(base + ruta);
    }

    // --- forma exacta de los DTO de ms-finanzas ---------------------------

    private record ReservarRequest(String jugadorUid, BigDecimal monto, String concepto, String referenciaId) { }

    private record ConsumirRequest(String vendedorUid) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ReservaJson(String reservaId, String jugadorUid, BigDecimal monto, String estado) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SaldoJson(String jugadorUid, BigDecimal saldoBruto,
                             BigDecimal saldoReservado, BigDecimal saldoDisponible) { }
}
