package com.nexusbattles.plataforma.salaspartidas.integracion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.CreditosDelJugador;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.ReservaDeCreditos;
import com.nexusbattles.plataforma.salaspartidas.dominio.CreditosInsuficientes;
import com.nexusbattles.plataforma.salaspartidas.dominio.CreditosNoDisponibles;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Adaptador del puerto de creditos contra ms-finanzas — HU-JUE-014.
 *
 * <p>Habla con {@code contracts/openapi/creditos.yaml} (equipo de Cuentas) con
 * cuatro llamadas y ninguna regla propia:
 *
 * <ol>
 *   <li>{@code POST /creditos/reservar} con {@code Idempotency-Key} — aparta
 *       los creditos de forma atomica del lado del libro.</li>
 *   <li>{@code POST /creditos/reservas/{id}/liberar} — los devuelve.</li>
 *   <li>{@code POST /creditos/reservas/{id}/consumir} — los cobra y se los
 *       acredita al beneficiario.</li>
 *   <li>{@code GET /creditos/{uid}/saldo} — solo tras un 422, para poder decir
 *       «tienes X y necesitas Y» como exige CA-02: el rechazo del libro no
 *       trae las cifras.</li>
 * </ol>
 *
 * <p><b>Clave de idempotencia.</b> {@code sala-<idSala>-jugador-<uid>-v<ingreso>}.
 * Es determinista a proposito: si este servicio pierde la respuesta y el
 * jugador reintenta el mismo ingreso, la misma clave devuelve la misma reserva
 * en vez de apartar dos veces. Y cambia cuando cambia la sala (la version),
 * asi que quien vuelve a entrar despues de irse consigue una reserva nueva y
 * no la que ya se le devolvio (ver {@code CreditosDelJugador#reservar}).
 *
 * <p><b>Fallo cerrado.</b> Si el libro no contesta o responde algo que no se
 * entiende, se lanza {@link CreditosNoDisponibles} (503) y no se inventa
 * ninguna reserva. Un 409 al consumir («la reserva ya se libero») tambien es
 * un fallo: significa que este servicio y el libro no coinciden, y eso hay que
 * verlo, no taparlo.
 *
 * <p><b>Autenticacion.</b> El {@code RestClient} que recibe ya lleva el
 * interceptor de credencial de servicio (ADR-001/ADR-005) cuando esta
 * configurado; aqui no se anade ninguna cabecera de identidad.
 */
public class ClienteCreditos implements CreditosDelJugador {

    static final String CONCEPTO = "apuesta-sala";

    private final RestClient http;
    private final String base;

    public ClienteCreditos(RestClient http, String base) {
        this.http = http;
        this.base = base.replaceAll("/+$", "");
    }

    /** Clave unica por (sala, jugador, ingreso); visible para la prueba de contrato. */
    static String claveDeIdempotencia(UUID idSala, UUID idJugador, long ingreso) {
        return "sala-" + idSala + "-jugador-" + idJugador + "-v" + ingreso;
    }

    @Override
    public ReservaDeCreditos reservar(UUID idJugador, int creditos, UUID idSala, long ingreso) {
        if (creditos <= 0) {
            throw new IllegalArgumentException("Solo se reserva una cantidad positiva de creditos.");
        }
        try {
            Reserva respuesta = http.post()
                    .uri(base + "/creditos/reservar")
                    .header("Idempotency-Key", claveDeIdempotencia(idSala, idJugador, ingreso))
                    .body(new PeticionDeReserva(idJugador.toString(), BigDecimal.valueOf(creditos),
                            CONCEPTO, "sala-" + idSala))
                    .retrieve()
                    .body(Reserva.class);

            if (respuesta == null || respuesta.reservaId() == null || respuesta.monto() == null) {
                throw new CreditosNoDisponibles("el libro respondio una reserva que no se entiende");
            }
            return new ReservaDeCreditos(respuesta.reservaId(), enteros(respuesta.monto()));

        } catch (HttpClientErrorException error) {
            if (error.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY) {
                throw new CreditosInsuficientes(saldoDisponibleDe(idJugador), creditos);
            }
            throw new CreditosNoDisponibles("el libro rechazo la reserva con " + error.getStatusCode().value());
        } catch (RestClientException noResponde) {
            throw new CreditosNoDisponibles(noResponde.getMessage());
        }
    }

    @Override
    public void liberar(UUID idReserva) {
        try {
            http.post()
                    .uri(base + "/creditos/reservas/{id}/liberar", idReserva)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException noResponde) {
            throw new CreditosNoDisponibles("no se pudo liberar la reserva " + idReserva
                    + ": " + noResponde.getMessage());
        }
    }

    @Override
    public void consumir(UUID idReserva, UUID idBeneficiario) {
        try {
            http.post()
                    .uri(base + "/creditos/reservas/{id}/consumir", idReserva)
                    .body(new PeticionDeConsumo(idBeneficiario == null ? null : idBeneficiario.toString()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException noResponde) {
            throw new CreditosNoDisponibles("no se pudo cobrar la reserva " + idReserva
                    + ": " + noResponde.getMessage());
        }
    }

    /**
     * Saldo disponible, para completar el 422. Si esta segunda llamada tambien
     * falla se dice 0: el rechazo ya es cierto, y es mejor un 422 con una
     * cifra incompleta que convertirlo en un 503 que no lo es.
     */
    private int saldoDisponibleDe(UUID idJugador) {
        try {
            Saldo saldo = http.get()
                    .uri(base + "/creditos/{uid}/saldo", idJugador)
                    .retrieve()
                    .body(Saldo.class);
            return saldo == null || saldo.saldoDisponible() == null ? 0 : enteros(saldo.saldoDisponible());
        } catch (RestClientException noResponde) {
            return 0;
        }
    }

    /** El libro trabaja con decimales; las apuestas de este servicio son enteras. */
    private static int enteros(BigDecimal monto) {
        return monto.setScale(0, RoundingMode.DOWN).intValueExact();
    }

    // -- Formas exactas del contrato creditos.yaml -----------------------------

    record PeticionDeReserva(String jugadorUid, BigDecimal monto, String concepto, String referenciaId) { }

    record PeticionDeConsumo(String vendedorUid) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Reserva(UUID reservaId, String jugadorUid, BigDecimal monto, String estado) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Saldo(BigDecimal saldoBruto, BigDecimal saldoReservado, BigDecimal saldoDisponible) { }
}
