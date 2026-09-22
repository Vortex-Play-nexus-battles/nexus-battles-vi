package com.nexusbattles.plataforma.torneos.integracion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexusbattles.plataforma.torneos.torneo.LibroDeCreditos;
import com.nexusbattles.plataforma.torneos.torneo.TorneoRechazado;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.UUID;

/** creditos.yaml: reservar / liberar / consumir, con clave idempotente del torneo. */
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
    public UUID reservar(UUID jugador, int creditos, String claveIdempotente, String referencia) {
        try {
            Reserva reserva = http.post()
                    .uri(base + "/creditos/reservar")
                    .header("Idempotency-Key", claveIdempotente)
                    .body(new PeticionDeReserva(jugador.toString(), BigDecimal.valueOf(creditos), CONCEPTO, referencia))
                    .retrieve()
                    .body(Reserva.class);
            if (reserva == null || reserva.reservaId() == null) {
                throw new TorneoRechazado(TorneoRechazado.Motivo.LIBRO_NO_DISPONIBLE,
                        "el libro respondio una reserva que no se entiende");
            }
            return reserva.reservaId();
        } catch (HttpClientErrorException rechazo) {
            if (rechazo.getStatusCode().value() == SALDO_INSUFICIENTE) {
                throw new TorneoRechazado(TorneoRechazado.Motivo.CREDITOS_INSUFICIENTES,
                        "no tienes " + creditos + " creditos disponibles para la inscripcion");
            }
            throw new TorneoRechazado(TorneoRechazado.Motivo.LIBRO_NO_DISPONIBLE,
                    "el libro rechazo la reserva con " + rechazo.getStatusCode().value());
        } catch (RestClientException noResponde) {
            throw new TorneoRechazado(TorneoRechazado.Motivo.LIBRO_NO_DISPONIBLE,
                    "el libro de creditos no responde: " + noResponde.getMessage());
        }
    }

    @Override
    public void liberar(UUID reservaId) {
        enviar("/creditos/reservas/{id}/liberar", reservaId, null, "liberar");
    }

    @Override
    public void consumir(UUID reservaId) {
        enviar("/creditos/reservas/{id}/consumir", reservaId, new PeticionDeConsumo(null), "cobrar");
    }

    private void enviar(String ruta, UUID reservaId, Object cuerpo, String que) {
        try {
            RestClient.RequestBodySpec peticion = http.post().uri(base + ruta, reservaId);
            if (cuerpo != null) {
                peticion.body(cuerpo);
            }
            peticion.retrieve().toBodilessEntity();
        } catch (RestClientException fallo) {
            throw new TorneoRechazado(TorneoRechazado.Motivo.LIBRO_NO_DISPONIBLE,
                    "no se pudo " + que + " la reserva " + reservaId + ": " + fallo.getMessage());
        }
    }

    record PeticionDeReserva(String jugadorUid, BigDecimal monto, String concepto, String referenciaId) { }

    record PeticionDeConsumo(String vendedorUid) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Reserva(UUID reservaId, String jugadorUid, BigDecimal monto, String estado) { }
}
