package com.nexusbattles.ms_finanzas.creditos.service;

import com.nexusbattles.ms_finanzas.common.exception.ReservaNoEncontradaException;
import com.nexusbattles.ms_finanzas.common.exception.SaldoInsuficienteException;
import com.nexusbattles.ms_finanzas.creditos.domain.CuentaCredito;
import com.nexusbattles.ms_finanzas.creditos.domain.ReservaCredito;
import com.nexusbattles.ms_finanzas.creditos.dto.CreditoDTOs.*;
import com.nexusbattles.ms_finanzas.creditos.repository.CuentaCreditoRepository;
import com.nexusbattles.ms_finanzas.creditos.repository.ReservaCreditoRepository;
import com.nexusbattles.ms_finanzas.transacciones.ResultadoTransaccion;
import com.nexusbattles.ms_finanzas.transacciones.Transaccion;
import com.nexusbattles.ms_finanzas.transacciones.TransaccionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Libro de creditos del jugador: saldo, reservas, debitos y acreditaciones.
 *
 * <h2>Nota de integracion (2026-09-17)</h2>
 *
 * <p>Las cinco clases de este paquete llegaron a {@code develop} con los
 * marcadores de conflicto de un merge sin resolver dentro del codigo
 * ({@code <<<<<<< HEAD}), asi que el servicio no compilaba. Al resolverlos se
 * tomo, bloque a bloque, la version que deja el comportamiento correcto:
 *
 * <ul>
 *   <li>La <b>idempotencia</b> de debito y reversion y la <b>compensacion
 *       real</b> de {@code reversar} se conservan: el otro lado eran cabos
 *       sueltos que devolvian {@code BigDecimal.ONE} sin tocar nada.</li>
 *   <li>Las operaciones directas (acreditar y debitar) se registran en la
 *       tabla <b>{@code transacciones}</b>, que existe para eso y ya tiene
 *       {@code ref_id} unico, en vez de reutilizar {@code reserva_credito}
 *       como libro de operaciones: una reserva es una retencion temporal con
 *       fecha de expiracion, y un debito consumado no es ninguna de las dos
 *       cosas. El test del modulo ya asumia esta tabla.</li>
 * </ul>
 */
@Service
public class CreditoService {

    private final CuentaCreditoRepository cuentaRepository;
    private final ReservaCreditoRepository reservaRepository;
    private final TransaccionRepository transaccionRepository;

    /** Moneda de las operaciones de credito (ISO 4217). */
    private static final String MONEDA = "COP";

    public CreditoService(CuentaCreditoRepository cuentaRepository,
                          ReservaCreditoRepository reservaRepository,
                          TransaccionRepository transaccionRepository) {
        this.cuentaRepository = cuentaRepository;
        this.reservaRepository = reservaRepository;
        this.transaccionRepository = transaccionRepository;
    }

    // Sin readOnly: obtenerOCrearCuenta da de alta la cuenta la primera vez
    // que se consulta, y el repositorio bloquea la fila con PESSIMISTIC_WRITE
    // (incompatible con una transaccion de solo lectura).
    @Transactional
    public SaldoResponse obtenerSaldo(String jugadorUid) {
        CuentaCredito cuenta = obtenerOCrearCuenta(jugadorUid);
        return new SaldoResponse(
            cuenta.getJugadorUid(),
            cuenta.getSaldoBruto(),
            cuenta.getSaldoReservado(),
            cuenta.getSaldoDisponible()
        );
    }

    @Transactional
    public ReservaResponse reservar(ReservarRequest req, String idempotencyKey) {
        // idempotency_key es NOT NULL UNIQUE en la tabla: una clave vacia
        // pasaria la busqueda y reventaria contra el indice al guardar la
        // segunda reserva, con un error de base de datos en vez de uno claro.
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(
                "La cabecera Idempotency-Key es obligatoria para reservar creditos.");
        }

        Optional<ReservaCredito> reservaExistente = reservaRepository.findByIdempotencyKey(idempotencyKey);
        if (reservaExistente.isPresent()) {
            ReservaCredito r = reservaExistente.get();
            return new ReservaResponse(r.getId(), r.getJugadorUid(), r.getMonto(), r.getEstado().name(), r.getExpiraEn());
        }

        CuentaCredito cuenta = obtenerOCrearCuenta(req.jugadorUid());

        if (cuenta.getSaldoDisponible().compareTo(req.monto()) < 0) {
            throw new SaldoInsuficienteException("Saldo insuficiente para reservar " + req.monto() + " crÃ©ditos.");
        }

        cuenta.setSaldoReservado(cuenta.getSaldoReservado().add(req.monto()));
        cuentaRepository.save(cuenta);

        ReservaCredito reserva = ReservaCredito.builder()
            .jugadorUid(req.jugadorUid())
            .monto(req.monto())
            .concepto(req.concepto())
            .referenciaId(req.referenciaId())
            .idempotencyKey(idempotencyKey)
            .estado(ReservaCredito.EstadoReserva.ACTIVA)
            // 72 horas y no 1: una reserva cubre una subasta, y las subastas
            // duran 24 o 48 horas (ms-subastas). Con una hora, la retencion
            // caducaba antes de que se pudiera cobrar la puja ganadora.
            .expiraEn(OffsetDateTime.now().plusHours(72))
            .build();

        ReservaCredito guardada = reservaRepository.save(reserva);
        return new ReservaResponse(guardada.getId(), guardada.getJugadorUid(), guardada.getMonto(), guardada.getEstado().name(), guardada.getExpiraEn());
    }

    @Transactional
    public ReservaResponse liberar(UUID reservaId) {
        ReservaCredito reserva = reservaRepository.findById(reservaId)
            .orElseThrow(() -> new ReservaNoEncontradaException("Reserva no encontrada con ID: " + reservaId));

        if (reserva.getEstado() != ReservaCredito.EstadoReserva.ACTIVA) {
            return new ReservaResponse(reserva.getId(), reserva.getJugadorUid(), reserva.getMonto(), reserva.getEstado().name(), reserva.getExpiraEn());
        }

        CuentaCredito cuenta = obtenerOCrearCuenta(reserva.getJugadorUid());
        cuenta.setSaldoReservado(cuenta.getSaldoReservado().subtract(reserva.getMonto()));
        cuentaRepository.save(cuenta);

        reserva.setEstado(ReservaCredito.EstadoReserva.LIBERADA);
        reservaRepository.save(reserva);

        return new ReservaResponse(reserva.getId(), reserva.getJugadorUid(), reserva.getMonto(), reserva.getEstado().name(), reserva.getExpiraEn());
    }

    @Transactional
    public ConsumirResponse consumir(UUID reservaId, ConsumirRequest req) {
        ReservaCredito reserva = reservaRepository.findById(reservaId)
            .orElseThrow(() -> new ReservaNoEncontradaException("Reserva no encontrada con ID: " + reservaId));

        if (reserva.getEstado() == ReservaCredito.EstadoReserva.CONSUMIDA) {
            return new ConsumirResponse(reserva.getId(), reserva.getEstado().name(), reserva.getMonto(), req.vendedorUid(), "TX-EXISTENTE");
        }

        CuentaCredito comprador = obtenerOCrearCuenta(reserva.getJugadorUid());
        comprador.setSaldoReservado(comprador.getSaldoReservado().subtract(reserva.getMonto()));
        comprador.setSaldoBruto(comprador.getSaldoBruto().subtract(reserva.getMonto()));
        cuentaRepository.save(comprador);

        if (req.vendedorUid() != null && !req.vendedorUid().isBlank()) {
            CuentaCredito vendedor = obtenerOCrearCuenta(req.vendedorUid());
            vendedor.setSaldoBruto(vendedor.getSaldoBruto().add(reserva.getMonto()));
            cuentaRepository.save(vendedor);
        }

        reserva.setEstado(ReservaCredito.EstadoReserva.CONSUMIDA);
        reservaRepository.save(reserva);

        String txId = "TX-CRED-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new ConsumirResponse(reserva.getId(), reserva.getEstado().name(), reserva.getMonto(), req.vendedorUid(), txId);
    }

    @Transactional
    public DebitarResponse debitar(DebitarRequest req) {
        // Idempotencia por refId: un reintento por timeout no puede cobrar dos
        // veces. La operacion anterior se reconoce por su transaccion.
        Optional<Transaccion> yaRegistrada = transaccionRepository.findByRefId(req.refId());
        if (yaRegistrada.isPresent()) {
            Transaccion previa = yaRegistrada.get();
            CuentaCredito cuenta = obtenerOCrearCuenta(req.uid());
            return new DebitarResponse(
                identificadorDe("TX-DEB", previa),
                req.refId(),
                "EXITOSO",
                previa.getMonto(),
                cuenta.getSaldoDisponible());
        }

        CuentaCredito cuenta = obtenerOCrearCuenta(req.uid());

        if (cuenta.getSaldoDisponible().compareTo(req.monto()) < 0) {
            throw new SaldoInsuficienteException("Saldo insuficiente para debitar " + req.monto() + " crÃ©ditos.");
        }

        cuenta.setSaldoBruto(cuenta.getSaldoBruto().subtract(req.monto()));
        cuentaRepository.save(cuenta);

        Transaccion registro = registrar(
            req.refId(),
            req.uid(),
            req.monto(),
            req.concepto() != null ? req.concepto() : "DEBITO-DIRECTO");

        return new DebitarResponse(
            identificadorDe("TX-DEB", registro),
            req.refId(),
            "EXITOSO",
            req.monto(),
            cuenta.getSaldoDisponible());
    }

    @Transactional
    public AcreditarResponse acreditar(AcreditarRequest req) {
        // Misma idempotencia que el debito: acreditar dos veces el mismo
        // refId regalaria creditos.
        Optional<Transaccion> yaRegistrada = transaccionRepository.findByRefId(req.refId());
        if (yaRegistrada.isPresent()) {
            CuentaCredito cuenta = obtenerOCrearCuenta(req.uid());
            return new AcreditarResponse(
                identificadorDe("TX-ACR", yaRegistrada.get()),
                req.refId(),
                "APLICADO",
                yaRegistrada.get().getMonto(),
                cuenta.getSaldoDisponible());
        }

        CuentaCredito cuenta = obtenerOCrearCuenta(req.uid());
        cuenta.setSaldoBruto(cuenta.getSaldoBruto().add(req.monto()));
        cuentaRepository.save(cuenta);

        Transaccion registro = registrar(
            req.refId(),
            req.uid(),
            req.monto(),
            req.concepto() != null ? req.concepto() : "ACREDITACION-DIRECTA");

        return new AcreditarResponse(
            identificadorDe("TX-ACR", registro),
            req.refId(),
            "APLICADO",
            req.monto(),
            cuenta.getSaldoDisponible());
    }

    @Transactional
    public ReversarResponse reversar(ReversarRequest req) {
        // Compensacion real: se busca el movimiento original y se devuelve el
        // saldo. Antes esto devolvia "REVERSADO" con un monto fijo sin tocar
        // ninguna cuenta, asi que un reverso no reponia nada.
        Transaccion original = transaccionRepository.findByRefId(req.refId())
            .orElseThrow(() -> new ReservaNoEncontradaException(
                "OperaciÃ³n no encontrada para reversar con refId: " + req.refId()));

        if (original.getResultado() == ResultadoTransaccion.REVERSADO) {
            return new ReversarResponse(req.refId(), "YA_REVERSADO", original.getMonto(), req.motivo());
        }

        CuentaCredito cuenta = obtenerOCrearCuenta(original.getUidUsuario());
        cuenta.setSaldoBruto(cuenta.getSaldoBruto().add(original.getMonto()));
        cuentaRepository.save(cuenta);

        original.setResultado(ResultadoTransaccion.REVERSADO);
        transaccionRepository.save(original);

        return new ReversarResponse(req.refId(), "REVERSADO", original.getMonto(), req.motivo());
    }

    @Transactional(readOnly = true)
    public OperacionResponse consultarOperacionPorRefId(String refId) {
        Transaccion operacion = transaccionRepository.findByRefId(refId)
            .orElseThrow(() -> new ReservaNoEncontradaException("OperaciÃ³n no encontrada con refId: " + refId));

        return new OperacionResponse(
            refId,
            operacion.getUidUsuario(),
            operacion.getMonto(),
            operacion.getConcepto(),
            operacion.getResultado().name(),
            operacion.getCreado() != null ? operacion.getCreado() : OffsetDateTime.now()
        );
    }

    /** Deja constancia de una operacion directa en el libro de transacciones. */
    private Transaccion registrar(String refId, String uid, BigDecimal monto, String concepto) {
        Transaccion transaccion = new Transaccion();
        transaccion.setRefId(refId);
        transaccion.setUidUsuario(uid);
        transaccion.setMonto(monto);
        transaccion.setMoneda(MONEDA);
        transaccion.setConcepto(concepto);
        transaccion.setResultado(ResultadoTransaccion.APROBADO);
        return transaccionRepository.save(transaccion);
    }

    /** Identificador legible de la operacion, derivado de su transaccion. */
    private static String identificadorDe(String prefijo, Transaccion transaccion) {
        String base = transaccion.getId() != null
                ? transaccion.getId().toString()
                : UUID.randomUUID().toString();
        return prefijo + "-" + base.substring(0, 8).toUpperCase();
    }

    private CuentaCredito obtenerOCrearCuenta(String jugadorUid) {
        return cuentaRepository.findByJugadorUid(jugadorUid)
            .orElseGet(() -> {
                try {
                    return cuentaRepository.save(CuentaCredito.builder()
                        .jugadorUid(jugadorUid)
                        .saldoBruto(BigDecimal.ZERO)
                        .saldoReservado(BigDecimal.ZERO)
                        .build());
                } catch (DataIntegrityViolationException e) {
                    // Dos peticiones simultaneas del mismo jugador pueden
                    // intentar crear la cuenta a la vez; la que pierde relee.
                    return cuentaRepository.findByJugadorUid(jugadorUid)
                        .orElseThrow(() -> new IllegalStateException(
                            "No se pudo crear ni encontrar la cuenta para el uid: " + jugadorUid, e));
                }
            });
    }
}
