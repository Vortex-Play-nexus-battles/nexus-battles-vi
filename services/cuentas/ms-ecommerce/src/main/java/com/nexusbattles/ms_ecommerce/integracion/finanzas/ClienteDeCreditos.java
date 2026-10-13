package com.nexusbattles.ms_ecommerce.integracion.finanzas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexusbattles.ms_ecommerce.integracion.ConfiguracionDeIntegraciones;
import com.nexusbattles.ms_ecommerce.integracion.ServicioNoDisponibleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * El libro de creditos de ms-finanzas (creditos.yaml) desde la tienda: pagar
 * una compra con los creditos del juego (D-44, contrato 1.6.0).
 *
 * <p>Solo con la credencial de servicio de la tienda: en ms-finanzas
 * {@code /creditos/**} es {@code ROLE_SERVICIO} (ADR-005) y el jugador viaja
 * en el cuerpo. La tienda usa cuatro operaciones que ya existen y no cambia
 * ninguna:
 *
 * <ul>
 *   <li>{@code POST /creditos/debitar}: el cobro. Idempotente por
 *       {@code refId} (clave unica en ms-finanzas): el mismo refId devuelve el
 *       debito original sin descontar otra vez. La tienda usa uno por orden
 *       ({@code tienda-orden-{id}}), asi que reintentar nunca cobra dos veces.</li>
 *   <li>{@code POST /creditos/reversar}: la devolucion de una compra que no se
 *       pudo entregar; la segunda vez responde YA_REVERSADO.</li>
 *   <li>{@code GET /creditos/operaciones/{refId}}: conciliar una orden que no
 *       supo si se cobro (la respuesta se perdio y nadie reintento).</li>
 *   <li>{@code GET /creditos/{uid}/saldo}: el saldo de la cotizacion.</li>
 * </ul>
 *
 * <p>La misma frontera que los demas clientes de la compra: lo que ms-finanzas
 * decide (cobrado, saldo insuficiente, no hay nada que devolver) vuelve como
 * resultado; lo que es una averia (no respondio, 5xx, credencial rechazada,
 * una respuesta que no se entiende), como {@link ServicioNoDisponibleException}.
 * Con una averia la orden se queda donde esta y se reintenta con el mismo
 * refId; nunca se concluye «no cobro» de algo que no se sabe.
 */
@Component
public class ClienteDeCreditos {

    private static final Logger log = LoggerFactory.getLogger(ClienteDeCreditos.class);

    static final String DEBITAR = "/creditos/debitar";
    static final String REVERSAR = "/creditos/reversar";
    static final String OPERACION = "/creditos/operaciones/{refId}";
    static final String SALDO = "/creditos/{uid}/saldo";

    private static final String SERVICIO = "ms-finanzas";
    /** Los {@code type} de ms-finanzas terminan en estos nombres (GlobalExceptionHandler). */
    private static final String SALDO_INSUFICIENTE = "/saldo-insuficiente";
    private static final String NO_ENCONTRADA = "/reserva-no-encontrada";

    /** Lo que ms-finanzas decidio sobre el cobro. */
    public sealed interface ResultadoDelCobro permits Cobrado, SaldoInsuficiente {
    }

    /** Descontado (o ya descontado antes con ese mismo refId). */
    public record Cobrado(String transaccionId) implements ResultadoDelCobro {
    }

    /** El saldo disponible no alcanza: no se desconto nada. */
    public record SaldoInsuficiente() implements ResultadoDelCobro {
    }

    /** Lo que paso al devolver. */
    public enum Devolucion {
        /** Devuelto ahora o antes (REVERSADO / YA_REVERSADO). */
        DEVUELTO,
        /** ms-finanzas no tiene un debito con ese refId: no hubo cobro que devolver. */
        NADA_QUE_DEVOLVER
    }

    /**
     * Una operacion del libro por su {@code refId}.
     *
     * @param estado CONSUMIDA = el debito esta aplicado; LIBERADA = se devolvio
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Operacion(String refId, String uid, BigDecimal monto, String estado) {

        /** El debito esta aplicado y no se devolvio. */
        public boolean cobrada() {
            return "CONSUMIDA".equals(estado);
        }
    }

    private final RestClient cliente;

    public ClienteDeCreditos(@Qualifier(ConfiguracionDeIntegraciones.FINANZAS) RestClient cliente) {
        this.cliente = cliente;
    }

    /**
     * Cobra la compra.
     *
     * @param creditos lo que se descuenta, en creditos enteros
     * @param refId    el de la orden; el mismo en cada reintento
     * @throws ServicioNoDisponibleException si ms-finanzas no respondio o no se
     *         entendio la respuesta: no se sabe si cobro, y el reintento con el
     *         mismo refId lo resuelve
     */
    public ResultadoDelCobro debitar(String uid, long creditos, String refId, String concepto) {
        Debitar cuerpo = new Debitar(uid, BigDecimal.valueOf(creditos), refId, concepto);
        try {
            return cliente.post()
                    .uri(DEBITAR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON)
                    .body(cuerpo)
                    .exchange((peticion, respuesta) -> {
                        int estado = respuesta.getStatusCode().value();
                        if (estado == 200 || estado == 201) {
                            Debitado debitado = leer(() -> respuesta.bodyTo(Debitado.class));
                            if (debitado != null && debitado.montoDebitado() != null
                                    && debitado.montoDebitado().compareTo(BigDecimal.valueOf(creditos)) != 0) {
                                log.error("ms-finanzas debito {} creditos con el refId {} y se esperaban {}",
                                        debitado.montoDebitado(), refId, creditos);
                            }
                            return new Cobrado(debitado == null ? null : debitado.transaccionId());
                        }
                        if (estado == 422 && tipo(leer(() -> respuesta.bodyTo(Problema.class))).endsWith(SALDO_INSUFICIENTE)) {
                            return new SaldoInsuficiente();
                        }
                        throw new ServicioNoDisponibleException(SERVICIO,
                                "ms-finanzas respondio " + estado + " al cobro " + refId);
                    });
        } catch (RestClientException | IllegalArgumentException fallo) {
            throw new ServicioNoDisponibleException(SERVICIO,
                    "No se pudo cobrar " + refId + " en ms-finanzas: " + fallo.getMessage(), fallo);
        }
    }

    /**
     * Devuelve el cobro de una compra que no se pudo entregar.
     *
     * @throws ServicioNoDisponibleException si ms-finanzas no respondio; la
     *         orden sigue por compensar y se reintenta
     */
    public Devolucion reversar(String refId, String motivo) {
        Reversar cuerpo = new Reversar(refId, motivo);
        try {
            return cliente.post()
                    .uri(REVERSAR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON)
                    .body(cuerpo)
                    .exchange((peticion, respuesta) -> {
                        int estado = respuesta.getStatusCode().value();
                        if (estado == 200) {
                            return Devolucion.DEVUELTO;
                        }
                        if (estado == 404 && tipo(leer(() -> respuesta.bodyTo(Problema.class))).endsWith(NO_ENCONTRADA)) {
                            return Devolucion.NADA_QUE_DEVOLVER;
                        }
                        throw new ServicioNoDisponibleException(SERVICIO,
                                "ms-finanzas respondio " + estado + " a la devolucion " + refId);
                    });
        } catch (RestClientException | IllegalArgumentException fallo) {
            throw new ServicioNoDisponibleException(SERVICIO,
                    "No se pudo devolver " + refId + " en ms-finanzas: " + fallo.getMessage(), fallo);
        }
    }

    /**
     * La operacion del libro con ese refId, o vacio si ms-finanzas dice que no
     * existe (nunca se cobro).
     *
     * @throws ServicioNoDisponibleException si no hubo una respuesta clara: no
     *         se concluye «no cobro» de algo que no se sabe
     */
    public Optional<Operacion> operacion(String refId) {
        try {
            return cliente.get()
                    .uri(OPERACION, refId)
                    .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON)
                    .exchange((peticion, respuesta) -> {
                        int estado = respuesta.getStatusCode().value();
                        if (estado == 200) {
                            Operacion operacion = leer(() -> respuesta.bodyTo(Operacion.class));
                            if (operacion == null || operacion.estado() == null) {
                                throw new ServicioNoDisponibleException(SERVICIO,
                                        "ms-finanzas respondio la operacion " + refId + " sin estado");
                            }
                            return Optional.of(operacion);
                        }
                        if (estado == 404 && tipo(leer(() -> respuesta.bodyTo(Problema.class))).endsWith(NO_ENCONTRADA)) {
                            return Optional.<Operacion>empty();
                        }
                        throw new ServicioNoDisponibleException(SERVICIO,
                                "ms-finanzas respondio " + estado + " a la consulta de " + refId);
                    });
        } catch (RestClientException | IllegalArgumentException fallo) {
            throw new ServicioNoDisponibleException(SERVICIO,
                    "No se pudo consultar " + refId + " en ms-finanzas: " + fallo.getMessage(), fallo);
        }
    }

    /**
     * El saldo disponible del jugador en creditos enteros (hacia abajo), para
     * la cotizacion. Vacio si ms-finanzas no respondio: la cotizacion sale
     * igual, sin saldo, y el cobro lo vuelve a mirar.
     */
    public OptionalLong saldoDisponible(String uid) {
        try {
            Saldo saldo = cliente.get()
                    .uri(SALDO, uid)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange((peticion, respuesta) -> respuesta.getStatusCode().value() == 200
                            ? leer(() -> respuesta.bodyTo(Saldo.class))
                            : null);
            if (saldo == null || saldo.saldoDisponible() == null) {
                return OptionalLong.empty();
            }
            return OptionalLong.of(saldo.saldoDisponible().setScale(0, RoundingMode.FLOOR).longValueExact());
        } catch (RestClientException | IllegalArgumentException | ArithmeticException fallo) {
            log.warn("ms-finanzas no dio el saldo de la cotizacion: {}", fallo.getMessage());
            return OptionalLong.empty();
        }
    }

    // ------------------------------------------------------------ apoyo

    /** Lee un cuerpo sin dejar que uno vacio o raro tumbe la decision: null si no se pudo. */
    private static <T> T leer(Lector<T> lector) {
        try {
            return lector.leer();
        } catch (RuntimeException | java.io.IOException ilegible) {
            return null;
        }
    }

    private static String tipo(Problema problema) {
        return problema == null || problema.type() == null ? "" : problema.type();
    }

    @FunctionalInterface
    private interface Lector<T> {
        T leer() throws java.io.IOException;
    }

    /** {@code DebitarRequest} de ms-finanzas. */
    record Debitar(String uid, BigDecimal monto, String refId, String concepto) {
    }

    /** {@code ReversarRequest} de ms-finanzas. */
    record Reversar(String refId, String motivo) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Debitado(String transaccionId, String refId, String estado, BigDecimal montoDebitado) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Saldo(BigDecimal saldoDisponible) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Problema(String type) {
    }
}
