package com.nexusbattles.ms_finanzas.creditos.service;

import com.nexusbattles.ms_finanzas.common.exception.ReservaNoEncontradaException;
import com.nexusbattles.ms_finanzas.common.exception.SaldoInsuficienteException;
import com.nexusbattles.ms_finanzas.creditos.domain.CuentaCredito;
import com.nexusbattles.ms_finanzas.creditos.domain.ReservaCredito;
import com.nexusbattles.ms_finanzas.creditos.domain.TransaccionCredito;
import com.nexusbattles.ms_finanzas.creditos.dto.CreditoDTOs.*;
import com.nexusbattles.ms_finanzas.creditos.repository.CuentaCreditoRepository;
import com.nexusbattles.ms_finanzas.creditos.repository.ReservaCreditoRepository;
import com.nexusbattles.ms_finanzas.creditos.repository.TransaccionCreditoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class CreditoService {

    private final CuentaCreditoRepository cuentaRepository;
    private final ReservaCreditoRepository reservaRepository;
    private final TransaccionCreditoRepository transaccionRepository;

    public CreditoService(CuentaCreditoRepository cuentaRepository,
                          ReservaCreditoRepository reservaRepository,
                          TransaccionCreditoRepository transaccionRepository) {
        this.cuentaRepository = cuentaRepository;
        this.reservaRepository = reservaRepository;
        this.transaccionRepository = transaccionRepository;
    }

    @Transactional(readOnly = true)
    public SaldoResponse obtenerSaldo(String jugadorUid) {
        CuentaCredito cuenta = obtenerOCrearCuenta(jugadorUid);
        return new SaldoResponse(cuenta.getJugadorUid(), cuenta.getSaldoBruto(), cuenta.getSaldoReservado(), cuenta.getSaldoDisponible());
    }

    @Transactional
    public ReservaResponse reservar(ReservarRequest req, String idempotencyKey) {
        var reservaExistente = reservaRepository.findByIdempotencyKey(idempotencyKey);
        if (reservaExistente.isPresent()) {
            ReservaCredito r = reservaExistente.get();
            return new ReservaResponse(r.getId(), r.getJugadorUid(), r.getMonto(), r.getEstado().name(), r.getExpiraEn());
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
            .expiraEn(OffsetDateTime.now().plusHours(1))
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
        var txExistente = transaccionRepository.findByRefId(req.refId());
        if (txExistente.isPresent()) {
            CuentaCredito c = obtenerOCrearCuenta(req.uid());
            return new DebitarResponse(txExistente.get().getId().toString(), req.refId(), "EXITOSO", txExistente.get().getMonto(), c.getSaldoDisponible());
        }

        CuentaCredito cuenta = obtenerOCrearCuenta(req.uid());
        if (cuenta.getSaldoDisponible().compareTo(req.monto()) < 0) {
            throw new SaldoInsuficienteException("Saldo insuficiente para debitar comisión.");
        }

        cuenta.setSaldoBruto(cuenta.getSaldoBruto().subtract(req.monto()));
        cuentaRepository.save(cuenta);

        TransaccionCredito tx = TransaccionCredito.builder()
            .refId(req.refId())
            .jugadorUid(req.uid())
            .monto(req.monto())
            .concepto(req.concepto())
            .tipo("DEBITO")
            .estado("EXITOSO")
            .build();
        transaccionRepository.save(tx);

        return new DebitarResponse(tx.getId().toString(), req.refId(), "EXITOSO", req.monto(), cuenta.getSaldoDisponible());
    }

    @Transactional
    public ReversarResponse reversar(ReversarRequest req) {
        TransaccionCredito txOriginal = transaccionRepository.findByRefId(req.refId())
            .orElseThrow(() -> new ReservaNoEncontradaException("No existe transacción previa con refId: " + req.refId()));

        if ("REVERSADO".equals(txOriginal.getEstado())) {
            return new ReversarResponse(req.refId(), "REVERSADO", txOriginal.getMonto(), req.motivo());
        }

        CuentaCredito cuenta = obtenerOCrearCuenta(txOriginal.getJugadorUid());
        cuenta.setSaldoBruto(cuenta.getSaldoBruto().add(txOriginal.getMonto()));
        cuentaRepository.save(cuenta);

        txOriginal.setEstado("REVERSADO");
        transaccionRepository.save(txOriginal);

        return new ReversarResponse(req.refId(), "REVERSADO", txOriginal.getMonto(), req.motivo());
    }

    @Transactional(readOnly = true)
    public OperacionResponse consultarOperacionPorRefId(String refId) {
        TransaccionCredito tx = transaccionRepository.findByRefId(refId)
            .orElseThrow(() -> new ReservaNoEncontradaException("Operación no encontrada para refId: " + refId));

        return new OperacionResponse(tx.getRefId(), tx.getJugadorUid(), tx.getMonto(), tx.getConcepto(), tx.getEstado(), tx.getCreado());
    }

    private CuentaCredito obtenerOCrearCuenta(String jugadorUid) {
        return cuentaRepository.findByJugadorUid(jugadorUid)
            .orElseGet(() -> cuentaRepository.save(CuentaCredito.builder()
                .jugadorUid(jugadorUid)
                .saldoBruto(BigDecimal.ZERO)
                .saldoReservado(BigDecimal.ZERO)
                .build()));
    }
}
