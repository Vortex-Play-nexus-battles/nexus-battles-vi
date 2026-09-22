package com.nexusbattles.plataforma.salaspartidas.integracion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexusbattles.plataforma.resiliencia.CortaCircuitos;
import com.nexusbattles.plataforma.resiliencia.DependenciaDegradada;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.CreditosDelJugador;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.ReservaDeCreditos;
import com.nexusbattles.plataforma.salaspartidas.dominio.CreditosInsuficientes;
import com.nexusbattles.plataforma.salaspartidas.dominio.CreditosNoDisponibles;
import org.springframework.http.ResponseEntity;
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
 * <p><b>Fallo cerrado, en dos formas.</b> Si el libro <i>no contesta</i>
 * (conexion rechazada, tiempo agotado, 5xx) la llamada atraviesa el corta
 * circuitos de HU-DIS-003 y sale como {@link DependenciaDegradada}: 503 con
 * {@code type} {@code seccion-no-disponible}, la seccion «Apuesta de creditos»
 * y {@code Retry-After}; tras varios fallos seguidos el circuito se abre y se
 * deja de llamar hasta que toque reintentar. Si el libro <i>contesta algo que
 * no sirve</i> (un 4xx distinto de 422, una reserva sin identificador) se lanza
 * {@link CreditosNoDisponibles} (503, {@code creditos-no-disponibles}): el libro
 * esta vivo y el circuito no se abre. En ninguno de los dos casos se inventa una
 * reserva. Un 409 al consumir («la reserva ya se libero») es de los segundos:
 * este servicio y el libro no coinciden, y eso hay que verlo, no taparlo.
 *
 * <p><b>Autenticacion.</b> El {@code RestClient} que recibe ya lleva el
 * interceptor de credencial de servicio (ADR-001/ADR-005) cuando esta
 * configurado; aqui no se anade ninguna cabecera de identidad.
 */
public class ClienteCreditos implements CreditosDelJugador {

    static final String CONCEPTO = "apuesta-sala";

    /** Codigo con el que creditos.yaml responde «saldo insuficiente». */
    static final int SALDO_INSUFICIENTE = 422;

    private final RestClient http;
    private final String base;
    private final CortaCircuitos corta;

    public ClienteCreditos(RestClient http, String base, CortaCircuitos corta) {
        this.http = http;
        this.base = base.replaceAll("/+$", "");
        this.corta = corta;
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
        // Detras del corta circuitos: lo que no responde sale como
        // DependenciaDegradada; lo que el libro contesta (4xx) llega aqui.
        Contestacion<Reserva> contestacion = Contestacion.protegida(corta, () -> http.post()
                .uri(base + "/creditos/reservar")
                .header("Idempotency-Key", claveDeIdempotencia(idSala, idJugador, ingreso))
                .body(new PeticionDeReserva(idJugador.toString(), BigDecimal.valueOf(creditos),
                        CONCEPTO, "sala-" + idSala))
                .retrieve()
                .body(Reserva.class));

        if (contestacion.rechazada()) {
            // Por valor, no por constante: Spring 7 tiene dos constantes para
            // 422 (UNPROCESSABLE_CONTENT y la vieja UNPROCESSABLE_ENTITY) y una
            // respuesta real resuelve a la primera. Con `==` el 422 del libro
            // se convertia en 503 — lo destapo el E2E.
            if (contestacion.estado() == SALDO_INSUFICIENTE) {
                throw new CreditosInsuficientes(saldoDisponibleDe(idJugador), creditos);
            }
            throw new CreditosNoDisponibles("el libro rechazo la reserva con " + contestacion.estado());
        }

        Reserva respuesta = contestacion.cuerpo();
        if (respuesta == null || respuesta.reservaId() == null || respuesta.monto() == null) {
            throw new CreditosNoDisponibles("el libro respondio una reserva que no se entiende");
        }
        return new ReservaDeCreditos(respuesta.reservaId(), enteros(respuesta.monto()));
    }

    @Override
    public void liberar(UUID idReserva) {
        Contestacion<ResponseEntity<Void>> contestacion = Contestacion.protegida(corta, () -> http.post()
                .uri(base + "/creditos/reservas/{id}/liberar", idReserva)
                .retrieve()
                .toBodilessEntity());
        if (contestacion.rechazada()) {
            throw new CreditosNoDisponibles("no se pudo liberar la reserva " + idReserva
                    + ": el libro respondio " + contestacion.estado());
        }
    }

    @Override
    public void consumir(UUID idReserva, UUID idBeneficiario) {
        Contestacion<ResponseEntity<Void>> contestacion = Contestacion.protegida(corta, () -> http.post()
                .uri(base + "/creditos/reservas/{id}/consumir", idReserva)
                .body(new PeticionDeConsumo(idBeneficiario == null ? null : idBeneficiario.toString()))
                .retrieve()
                .toBodilessEntity());
        if (contestacion.rechazada()) {
            throw new CreditosNoDisponibles("no se pudo cobrar la reserva " + idReserva
                    + ": el libro respondio " + contestacion.estado());
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
