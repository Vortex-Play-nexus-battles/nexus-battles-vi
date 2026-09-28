package com.nexusbattles.plataforma.torneos.integracion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexusbattles.plataforma.torneos.torneo.LibroDeCreditos;
import com.nexusbattles.plataforma.torneos.torneo.TorneoRechazado;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * creditos.yaml 1.4.0: reservar / liberar / consumir / acreditar.
 *
 * <p>Reservar la llama el jugador al inscribirse y falla con
 * {@link TorneoRechazado}; las demas las ejecuta el procesador de operaciones
 * y fallan con un {@code FalloDeIntegracion} clasificado (reintentable o no).
 * Todas son idempotentes en ms-finanzas: reservar por {@code Idempotency-Key},
 * liberar y consumir por la reserva, acreditar por {@code refId}.
 */
public class ClienteCreditos implements LibroDeCreditos {

    static final String CONCEPTO = "inscripcion-torneo";
    static final int SALDO_INSUFICIENTE = 422;

    private final RestClient http;
    private final String base;

    public ClienteCreditos(RestClient http, String base) {
        this.http = http;
        this.base = base.replaceAll("/+$", "");
    }

    @Override
    public Reserva reservar(UUID jugador, int creditos, String claveIdempotente, String referencia) {
        try {
            RespuestaDeReserva reserva = http.post()
                    .uri(base + "/creditos/reservar")
                    .header("Idempotency-Key", claveIdempotente)
                    .body(new PeticionDeReserva(jugador.toString(), BigDecimal.valueOf(creditos), CONCEPTO, referencia))
                    .retrieve()
                    .body(RespuestaDeReserva.class);
            if (reserva == null || reserva.reservaId() == null) {
                throw new TorneoRechazado(TorneoRechazado.Motivo.LIBRO_NO_DISPONIBLE,
                        "el libro respondio una reserva que no se entiende");
            }
            return new Reserva(reserva.reservaId(), reserva.estado() == null ? "ACTIVA" : reserva.estado());
        } catch (HttpClientErrorException rechazo) {
            if (rechazo.getStatusCode().value() == SALDO_INSUFICIENTE) {
                throw new TorneoRechazado(TorneoRechazado.Motivo.CREDITOS_INSUFICIENTES,
                        "no tienes " + creditos + " creditos disponibles para la inscripcion");
            }
            throw new TorneoRechazado(TorneoRechazado.Motivo.LIBRO_NO_DISPONIBLE,
                    "el libro rechazo la reserva con " + rechazo.getStatusCode().value());
        } catch (RestClientException noResponde) {
            throw new TorneoRechazado(TorneoRechazado.Motivo.LIBRO_NO_DISPONIBLE,
                    "el libro de creditos no responde: " + noResponde.getClass().getSimpleName());
        }
    }

    @Override
    public String liberar(UUID reservaId) {
        try {
            RespuestaDeReserva reserva = http.post()
                    .uri(base + "/creditos/reservas/{id}/liberar", reservaId)
                    .retrieve()
                    .body(RespuestaDeReserva.class);
            return reserva == null || reserva.estado() == null ? "LIBERADA" : reserva.estado();
        } catch (RestClientException fallo) {
            throw ClasificadorDeFallos.clasificar("liberar la reserva " + reservaId, fallo, true);
        }
    }

    @Override
    public void consumir(UUID reservaId) {
        try {
            http.post()
                    .uri(base + "/creditos/reservas/{id}/consumir", reservaId)
                    .body(new PeticionDeConsumo(null))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException fallo) {
            // 409 (reserva ya liberada) y 404 (no existe) son definitivos: el
            // dinero ya no esta apartado y otro intento no lo va a cobrar.
            throw ClasificadorDeFallos.clasificar("cobrar la reserva " + reservaId, fallo, true);
        }
    }

    @Override
    public void acreditar(UUID jugador, int creditos, String refId, String concepto) {
        try {
            http.post()
                    .uri(base + "/creditos/acreditar")
                    .body(new PeticionDeCredito(jugador.toString(), BigDecimal.valueOf(creditos), refId, concepto))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException fallo) {
            throw ClasificadorDeFallos.clasificar("acreditar " + creditos + " creditos (" + refId + ")", fallo, false);
        }
    }

    record PeticionDeReserva(String jugadorUid, BigDecimal monto, String concepto, String referenciaId) { }

    record PeticionDeConsumo(String vendedorUid) { }

    record PeticionDeCredito(String uid, BigDecimal monto, String refId, String concepto) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespuestaDeReserva(UUID reservaId, String jugadorUid, BigDecimal monto, String estado) { }
}
