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
import java.util.Objects;
import java.util.UUID;

import com.nexusbattles.comun.seguridad.servicio.CredencialDeServicioNoDisponible;
import com.nexusbattles.ms_subastas.seguridad.PortadorDeServicio;

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
 * <p><b>Los errores se deciden por el {@code type} del problem+json, no por el
 * codigo de estado.</b> El codigo solo no basta: un 404 de "esa reserva no
 * existe" y un 404 de "me equivoque de ruta" son el mismo numero, y el segundo
 * es un fallo propio que no debe disfrazarse de estado de negocio. ms-finanzas
 * publica {@code .../errors/saldo-insuficiente} (422) y
 * {@code .../errors/reserva-no-encontrada} (404) desde el 15/09/2026, que es lo
 * que hace posible separar "no tienes creditos" —que el jugador debe ver y que
 * no se reintenta— de "el servicio se cayo" —que si se reintenta y si debe
 * empujar el cortacircuitos.
 */
public class CreditoClientHttp implements CreditoClient {

    private final URI baseUri;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Duration timeout;
    private final PortadorDeServicio credencial;

    public CreditoClientHttp(String baseUrl, long timeoutMs, ObjectMapper objectMapper) {
        this(baseUrl, timeoutMs, objectMapper, PortadorDeServicio.ninguno());
    }

    /**
     * @param credencial la de ms-subastas ante ms-finanzas (ADR-005). Desde que
     *        {@code /creditos/**} exige {@code ROLE_SERVICIO} (#455), sin ella
     *        toda llamada vuelve con 401.
     */
    public CreditoClientHttp(String baseUrl, long timeoutMs, ObjectMapper objectMapper,
                             PortadorDeServicio credencial) {
        this(URI.create(baseUrl),
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build(),
                objectMapper, Duration.ofMillis(timeoutMs), credencial);
    }

    public CreditoClientHttp(URI baseUri, HttpClient httpClient, ObjectMapper objectMapper, Duration timeout) {
        this(baseUri, httpClient, objectMapper, timeout, PortadorDeServicio.ninguno());
    }

    public CreditoClientHttp(URI baseUri, HttpClient httpClient, ObjectMapper objectMapper, Duration timeout,
                             PortadorDeServicio credencial) {
        this.baseUri = baseUri;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.timeout = timeout;
        this.credencial = Objects.requireNonNull(credencial, "credencial");
    }

    @Override
    public ReservaCredito reservar(UUID jugadorId, BigDecimal monto, UUID subastaId, String idempotencyKey) {
        exigir(jugadorId != null, "El jugador es obligatorio para reservar");
        exigir(monto != null && monto.signum() > 0, "El monto a reservar debe ser positivo");
        exigir(subastaId != null, "La subasta es obligatoria para reservar");
        // ms-finanzas la declara obligatoria: sin ella responde 400.
        exigir(idempotencyKey != null && !idempotencyKey.isBlank(),
                "La clave de idempotencia es obligatoria para reservar creditos");

        HttpRequest peticion = firmada(HttpRequest.newBuilder(uri("/creditos/reservar")))
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

        HttpRequest peticion = firmada(HttpRequest.newBuilder(uri("/creditos/reservas/" + reservaId + "/liberar")))
                .header("Accept", "application/json")
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> respuesta = enviar(peticion, "liberar la reserva");

        // Si la reserva no existe, no queda nada que liberar: el efecto buscado
        // ya se cumple. Y tratarlo como error seria peor que inutil — liberar se
        // llama desde el cierre por vencimiento, que corre en transaccion: al
        // fallar revierte, la subasta se queda ACTIVA y el job la reintenta cada
        // 30 s para siempre. Mismo criterio que con el 404 de inventario.
        if (tipoDelProblema(respuesta).endsWith("reserva-no-encontrada")) {
            return;
        }
        exigirExito(respuesta, "liberar la reserva");
    }

    @Override
    public void consumir(UUID reservaId, UUID vendedorId) {
        exigir(reservaId != null, "La reserva es obligatoria para consumir");

        // vendedorUid puede ir nulo y ms-finanzas lo admite: entonces cobra al
        // comprador sin abonar a nadie. Aqui no se deja pasar, porque en una
        // subasta siempre hay a quien pagarle y hacerlo en silencio seria
        // quedarse los creditos por el camino.
        exigir(vendedorId != null, "El vendedor es obligatorio: sin el, el comprador paga y nadie cobra");

        HttpRequest peticion = firmada(HttpRequest.newBuilder(uri("/creditos/reservas/" + reservaId + "/consumir")))
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

        HttpRequest peticion = firmada(HttpRequest.newBuilder(uri("/creditos/" + jugadorId + "/saldo")))
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

        // Se decide por el 'type' del problem+json, no por el codigo. El codigo
        // solo no basta: un 404 de "esa reserva no existe" y un 404 de "me
        // equivoque de ruta" son el mismo numero, y el segundo es un fallo mio
        // que no debe disfrazarse de estado de negocio. El 'type' los separa.
        String tipo = tipoDelProblema(respuesta);

        if (tipo.endsWith("saldo-insuficiente")) {
            throw new CreditoClientException(CreditoClientException.Motivo.SALDO_INSUFICIENTE,
                    "El jugador no tiene creditos suficientes al " + queSeIntentaba);
        }
        if (tipo.endsWith("reserva-no-encontrada")) {
            throw new CreditoClientException(CreditoClientException.Motivo.RESERVA_INEXISTENTE,
                    "ms-finanzas no reconoce la reserva al " + queSeIntentaba);
        }
        // Consumir una reserva que ya se devolvio. No es una averia y no se
        // reintenta —volveria a fallar igual—, pero tampoco se puede tragar:
        // significa que al ganador no se le va a poder cobrar, y eso tiene que
        // llegar arriba con su nombre y no disfrazado de "respuesta inesperada".
        if (tipo.endsWith("reserva-ya-liberada")) {
            throw new CreditoClientException(CreditoClientException.Motivo.RESERVA_YA_LIBERADA,
                    "ms-finanzas ya habia liberado la reserva al " + queSeIntentaba);
        }

        if (estado >= 500) {
            throw new CreditoNoDisponibleException(
                    "ms-finanzas respondio " + estado + " al " + queSeIntentaba);
        }
        throw new CreditoClientException(CreditoClientException.Motivo.RESPUESTA_INESPERADA,
                "ms-finanzas respondio " + estado + " al " + queSeIntentaba
                        + (tipo.isEmpty() ? " sin type en el cuerpo" : " con type " + tipo));
    }

    /**
     * El {@code type} del problem+json, o cadena vacia si la respuesta no lo
     * trae o no es JSON. Nunca lanza: esto se usa justamente en el camino de
     * error, y fallar aqui taparia el error de verdad con uno de parseo.
     */
    private String tipoDelProblema(HttpResponse<String> respuesta) {
        String cuerpo = respuesta.body();
        if (cuerpo == null || cuerpo.isBlank()) {
            return "";
        }
        try {
            ProblemaJson problema = objectMapper.readValue(cuerpo, ProblemaJson.class);
            return problema.type() == null ? "" : problema.type();
        } catch (IOException ilegible) {
            return "";
        }
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

    /**
     * Pone la credencial de ms-subastas (ADR-005). Si el emisor no la entrega,
     * la llamada no sale: para el motor de pujas es lo mismo que ms-finanzas
     * caido, y asi se reintenta y empuja el cortacircuitos igual.
     */
    private HttpRequest.Builder firmada(HttpRequest.Builder peticion) {
        try {
            return credencial.firmar(peticion);
        } catch (CredencialDeServicioNoDisponible sinCredencial) {
            throw new CreditoNoDisponibleException(
                    "Sin credencial de servicio no se puede llamar a ms-finanzas", sinCredencial);
        }
    }

    // --- forma exacta de los DTO de ms-finanzas ---------------------------

    private record ReservarRequest(String jugadorUid, BigDecimal monto, String concepto, String referenciaId) { }

    private record ConsumirRequest(String vendedorUid) { }

    /** Solo el 'type', que es lo unico del problem+json en lo que nos apoyamos. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProblemaJson(String type) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ReservaJson(String reservaId, String jugadorUid, BigDecimal monto, String estado) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SaldoJson(String jugadorUid, BigDecimal saldoBruto,
                             BigDecimal saldoReservado, BigDecimal saldoDisponible) { }
}
