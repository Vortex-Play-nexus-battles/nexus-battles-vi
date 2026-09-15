package com.nexusbattles.ms_finanzas.creditos.service;

import com.nexusbattles.ms_finanzas.common.exception.ReservaNoEncontradaException;
import com.nexusbattles.ms_finanzas.common.exception.SaldoInsuficienteException;
import com.nexusbattles.ms_finanzas.creditos.domain.CuentaCredito;
import com.nexusbattles.ms_finanzas.creditos.domain.ReservaCredito;
import com.nexusbattles.ms_finanzas.creditos.dto.CreditoDTOs.*;
import com.nexusbattles.ms_finanzas.creditos.repository.CuentaCreditoRepository;
import com.nexusbattles.ms_finanzas.creditos.repository.ReservaCreditoRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class CreditoService {

    private final CuentaCreditoRepository cuentaRepository;
    private final ReservaCreditoRepository reservaRepository;

    public CreditoService(CuentaCreditoRepository cuentaRepository,
                          ReservaCreditoRepository reservaRepository) {
        this.cuentaRepository = cuentaRepository;
        this.reservaRepository = reservaRepository;
    }

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
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<ReservaCredito> reservaExistente = reservaRepository.findByIdempotencyKey(idempotencyKey);
            if (reservaExistente.isPresent()) {
                ReservaCredito r = reservaExistente.get();
                return new ReservaResponse(r.getId(), r.getJugadorUid(), r.getMonto(), r.getEstado().name(), r.getExpiraEn());
            }
        }

        CuentaCredito cuenta = obtenerOCrearCuenta(req.jugadorUid());

        if (cuenta.getSaldoDisponible().compareTo(req.monto()) < 0) {
            throw new SaldoInsuficienteException("Saldo insuficiente para reservar " + req.monto() + " créditos.");
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
            .expiraEn(OffsetDateTime.now().plusHours(72)) // Ampliado a 72h para cubrir subastas de 24h/48h sin expirar prematuramente
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
        // Idempotencia por refId para Edwin (evita cobros duplicados ante timeouts)
        Optional<ReservaCredito> operacionExistente = reservaRepository.findByIdempotencyKey(req.refId());
        if (operacionExistente.isPresent()) {
            ReservaCredito op = operacionExistente.get();
            CuentaCredito cuenta = obtenerOCrearCuenta(req.uid());
            return new DebitarResponse("TX-DEB-" + op.getId().toString().substring(0, 8).toUpperCase(), req.refId(), "EXITOSO", op.getMonto(), cuenta.getSaldoDisponible());
        }

        CuentaCredito cuenta = obtenerOCrearCuenta(req.uid());

        if (cuenta.getSaldoDisponible().compareTo(req.monto()) < 0) {
            throw new SaldoInsuficienteException("Saldo insuficiente para debitar la comisión.");
        }

        cuenta.setSaldoBruto(cuenta.getSaldoBruto().subtract(req.monto()));
        cuentaRepository.save(cuenta);

        // Registramos la operación usando la tabla de reservas con idempotencyKey = refId para control persistente
        ReservaCredito registroOp = ReservaCredito.builder()
            .jugadorUid(req.uid())
            .monto(req.monto())
            .concepto(req.concepto() != null ? req.concepto() : "DEBITO-DIRECTO")
            .referenciaId(req.refId())
            .idempotencyKey(req.refId())
            .estado(ReservaCredito.EstadoReserva.CONSUMIDA)
            .expiraEn(OffsetDateTime.now().plusDays(72))
            .build();
        reservaRepository.save(registroOp);

        String txId = "TX-DEB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new DebitarResponse(txId, req.refId(), "EXITOSO", req.monto(), cuenta.getSaldoDisponible());
    }

    @Transactional
    public ReversarResponse reversar(ReversarRequest req) {
        // Lógica real de compensación para Edwin: buscar el débito original por refId y devolver el saldo
        Optional<ReservaCredito> operacionOpt = reservaRepository.findByIdempotencyKey(req.refId());

        if (operacionOpt.isEmpty()) {
            throw new ReservaNoEncontradaException("Operación no encontrada para reversar con refId: " + req.refId());
        }

        ReservaCredito op = operacionOpt.get();
        if (op.getEstado() == ReservaCredito.EstadoReserva.LIBERADA) {
            return new ReversarResponse(req.refId(), "YA_REVERSADO", op.getMonto(), req.motivo());
        }

        CuentaCredito cuenta = obtenerOCrearCuenta(op.getJugadorUid());
        cuenta.setSaldoBruto(cuenta.getSaldoBruto().add(op.getMonto()));
        cuentaRepository.save(cuenta);

        op.setEstado(ReservaCredito.EstadoReserva.LIBERADA);
        reservaRepository.save(op);

        return new ReversarResponse(req.refId(), "REVERSADO", op.getMonto(), req.motivo());
    }

    @Transactional(readOnly = true)
    public OperacionResponse consultarOperacionPorRefId(String refId) {
        ReservaCredito op = reservaRepository.findByIdempotencyKey(refId)
            .orElseThrow(() -> new ReservaNoEncontradaException("Operación no encontrada con refId: " + refId));

        return new OperacionResponse(
            refId,
            op.getJugadorUid(),
            op.getMonto(),
            op.getConcepto(),
            op.getEstado().name(),
            op.getCreado() != null ? op.getCreado() : OffsetDateTime.now()
        );
    }

    @Transactional
    public AcreditarResponse acreditar(AcreditarRequest req) {
        CuentaCredito cuenta = obtenerOCrearCuenta(req.uid());
        cuenta.setSaldoBruto(cuenta.getSaldoBruto().add(req.monto()));
        cuentaRepository.save(cuenta);

        String txId = "TX-ACR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new AcreditarResponse(txId, req.refId(), "APLICADO", req.monto(), cuenta.getSaldoDisponible());
    }

    private CuentaCredito obtenerOCrearCuenta(String jugadorUid) {
        return cuentaRepository.findByJugadorUid(jugadorUid)
            .orElseGet(() -> {
                try {
                    return cuentaRepository.save(CuentaCredito.builder()
                        .jugadorUid(jugadorUid)
                        .saldoBruto(BigDecimal.ZERO)
                        .saldoReservado(BigDecimal.ZERO)
                        .version(0L)
                        .build());
                } catch (DataIntegrityViolationException e) {
                    // Manejo seguro de concurrencia si dos hilos intentan crear la cuenta simultáneamente
                    return cuentaRepository.findByJugadorUid(jugadorUid)
                        .orElseThrow(() -> new IllegalStateException("No se pudo crear ni encontrar la cuenta para el uid: " + jugadorUid));
                }
            });
    }
}
