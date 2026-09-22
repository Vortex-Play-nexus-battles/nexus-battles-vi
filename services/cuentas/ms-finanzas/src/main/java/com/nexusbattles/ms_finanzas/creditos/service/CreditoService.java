package com.nexusbattles.ms_finanzas.creditos.service;

import com.nexusbattles.ms_finanzas.common.exception.ReservaNoEncontradaException;
import com.nexusbattles.ms_finanzas.common.exception.ReservaYaLiberadaException;
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

    public CreditoService(CuentaCreditoRepository cuentaRepository, ReservaCreditoRepository reservaRepository) {
        this.cuentaRepository = cuentaRepository;
        this.reservaRepository = reservaRepository;
    }

    // FIX DEFECTO 3: Transacción de solo lectura usando el repositorio sin Lock para evitar el error 500
    @Transactional(readOnly = true)
    public SaldoResponse obtenerSaldo(String jugadorUid) {
        CuentaCredito cuenta = cuentaRepository.findByJugadorUidReadOnly(jugadorUid)
            .orElse(CuentaCredito.builder()
                .jugadorUid(jugadorUid)
                .saldoBruto(BigDecimal.ZERO)
                .saldoReservado(BigDecimal.ZERO)
                .build());

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
            .tipoOperacion(ReservaCredito.TipoOperacion.RESERVA)
            .expiraEn(OffsetDateTime.now().plusHours(72)) // Ampliado a 72h para cubrir subastas
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

        // FIX DEFECTO 1: Validar si la reserva ya fue liberada para evitar robar saldo
        if (reserva.getEstado() == ReservaCredito.EstadoReserva.LIBERADA) {
            throw new ReservaYaLiberadaException("La reserva ya fue liberada y no puede ser consumida.");
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
        // Idempotencia por refId para evitar cobros duplicados
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

        // Registramos la operación usando la tabla de reservas con idempotencyKey = refId
        ReservaCredito registroOp = ReservaCredito.builder()
            .jugadorUid(req.uid())
            .monto(req.monto())
            .concepto(req.concepto() != null ? req.concepto() : "DEBITO-DIRECTO")
            .referenciaId(req.refId())
            .idempotencyKey(req.refId())
            .estado(ReservaCredito.EstadoReserva.CONSUMIDA)
            .tipoOperacion(ReservaCredito.TipoOperacion.DEBITO)
            .expiraEn(OffsetDateTime.now().plusDays(72))
            .build();
        reservaRepository.save(registroOp);

        String txId = "TX-DEB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new DebitarResponse(txId, req.refId(), "EXITOSO", req.monto(), cuenta.getSaldoDisponible());
    }

    @Transactional
    public ReversarResponse reversar(ReversarRequest req) {
        // Lógica real de compensación: buscar el débito original por refId y devolver el saldo.
        //
        // FIX (reporte de Andrés): antes esta búsqueda no distinguía el tipo de
        // operación, así que un refId que coincidiera con una reserva de puja
        // ACTIVA (creada por reservar()) o con un crédito ya otorgado (acreditar())
        // se "reversaba" igual: se sumaba el monto a saldoBruto sin tocar
        // saldoReservado (creando saldo de la nada) y se marcaba la fila como
        // LIBERADA (dejando esa reserva inconsumible para siempre). reversar()
        // solo tiene sentido para compensar un DEBITO real — ahora se valida
        // explícitamente.
        Optional<ReservaCredito> operacionOpt = reservaRepository.findByIdempotencyKey(req.refId());

        if (operacionOpt.isEmpty() || operacionOpt.get().getTipoOperacion() != ReservaCredito.TipoOperacion.DEBITO) {
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

    // FIX: acreditar() es idempotente por refId, igual que debitar().
    // Sin esto, un reintento de red desde el llamador (Sanabria, HU-JUE-012)
    // duplicaría los créditos otorgados por el mismo resultado de partida.
    /**
     * Historial de movimientos de credito del jugador (#569, HU-PAG-002).
     *
     * <p>Solo lectura: no toca saldo. La fuente es `reservas_credito`, donde
     * cada operacion —apuesta reservada, cobrada o devuelta, recompensa por
     * jugar, inscripcion a un torneo— deja su fila. Hasta ahora el jugador
     * solo podia ver sus pagos en moneda real (`/transacciones/mi-historial`)
     * y por eso una partida con apuesta no aparecia en ninguna parte.
     *
     * @param jugadorUid identificador estable del jugador (ADR-002)
     * @param pagina     pagina y tamano; el controlador acota el tamano
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<MovimientoResponse> movimientos(
            String jugadorUid, org.springframework.data.domain.Pageable pagina) {
        return reservaRepository.findByJugadorUidOrderByCreadoDesc(jugadorUid, pagina)
                .map(CreditoService::comoMovimiento);
    }

    /**
     * El signo que la interfaz necesita para pintar la linea, decidido aqui
     * una sola vez: un credito suma, un debito y una reserva consumida restan,
     * una reserva activa aparta y una liberada no movio nada.
     */
    static MovimientoResponse comoMovimiento(ReservaCredito operacion) {
        String signo = switch (operacion.getTipoOperacion()) {
            case CREDITO -> "SUMA";
            case DEBITO -> "RESTA";
            case RESERVA -> switch (operacion.getEstado()) {
                case CONSUMIDA -> "RESTA";
                case ACTIVA -> "APARTA";
                case LIBERADA -> "NEUTRO";
            };
        };
        return new MovimientoResponse(
                operacion.getId(),
                operacion.getMonto(),
                operacion.getConcepto(),
                operacion.getReferenciaId(),
                operacion.getTipoOperacion().name(),
                operacion.getEstado().name(),
                signo,
                operacion.getCreado());
    }

    @Transactional
    public AcreditarResponse acreditar(AcreditarRequest req) {
        Optional<ReservaCredito> operacionExistente = reservaRepository.findByIdempotencyKey(req.refId());
        if (operacionExistente.isPresent()) {
            ReservaCredito op = operacionExistente.get();
            CuentaCredito cuentaExistente = obtenerOCrearCuenta(req.uid());
            return new AcreditarResponse(
                "TX-ACR-" + op.getId().toString().substring(0, 8).toUpperCase(),
                req.refId(),
                "APLICADO",
                op.getMonto(),
                cuentaExistente.getSaldoDisponible()
            );
        }

        CuentaCredito cuenta = obtenerOCrearCuenta(req.uid());
        cuenta.setSaldoBruto(cuenta.getSaldoBruto().add(req.monto()));
        cuentaRepository.save(cuenta);

        // Registramos la operación para que reintentos futuros con el mismo refId
        // encuentren este registro y no vuelvan a acreditar.
        ReservaCredito registroOp = ReservaCredito.builder()
            .jugadorUid(req.uid())
            .monto(req.monto())
            .concepto(req.concepto() != null ? req.concepto() : "CREDITO-PARTIDA")
            .referenciaId(req.refId())
            .idempotencyKey(req.refId())
            .estado(ReservaCredito.EstadoReserva.CONSUMIDA)
            .tipoOperacion(ReservaCredito.TipoOperacion.CREDITO)
            .expiraEn(OffsetDateTime.now().plusDays(72))
            .build();
        reservaRepository.save(registroOp);

        String txId = "TX-ACR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new AcreditarResponse(txId, req.refId(), "APLICADO", req.monto(), cuenta.getSaldoDisponible());
    }

    // FIX DEFECTO 2 (v2, corregido tras reporte de Andrés): Manejo de Race Condition
    // al crear cuentas.
    //
    // v1 (incorrecto): construía la entidad con .version(0L) explícito. Con @Version
    // no nulo, Spring Data la trata como "no nueva" y usa merge() en vez de persist().
    // merge() sobre una fila que no existe genera un UPDATE que afecta 0 filas, y JPA
    // lo reporta como ObjectOptimisticLockingFailureException — no como conflicto de
    // llave duplicada. Resultado: sobre una BD limpia, TODAS las operaciones fallaban,
    // porque el catch (DataIntegrityViolationException) nunca atrapaba esa excepción.
    //
    // v2 (correcto): se deja version en null en el builder. @PrePersist ya asigna
    // version = 0L al persistir, así que no hace falta setearlo a mano. Con version
    // null, Spring Data reconoce la entidad como nueva y usa persist() (INSERT real).
    // Si dos requests concurrentes intentan crear la misma cuenta, el segundo INSERT
    // choca contra la PK (jugador_uid) y lanza DataIntegrityViolationException, que
    // el catch sí atrapa correctamente.
    private CuentaCredito obtenerOCrearCuenta(String jugadorUid) {
        Optional<CuentaCredito> cuentaOpt = cuentaRepository.findByJugadorUid(jugadorUid);
        if (cuentaOpt.isPresent()) {
            return cuentaOpt.get();
        }

        try {
            return cuentaRepository.saveAndFlush(CuentaCredito.builder()
                .jugadorUid(jugadorUid)
                .saldoBruto(BigDecimal.ZERO)
                .saldoReservado(BigDecimal.ZERO)
                .build());
        } catch (DataIntegrityViolationException ganamosLaCarrera) {
            return cuentaRepository.findByJugadorUid(jugadorUid)
                .orElseThrow(() -> new IllegalStateException("No se pudo crear ni encontrar la cuenta para el uid: " + jugadorUid));
        }
    }
}
