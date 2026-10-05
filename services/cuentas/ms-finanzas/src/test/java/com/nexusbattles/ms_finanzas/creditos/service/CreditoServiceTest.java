package com.nexusbattles.ms_finanzas.creditos.service;

import com.nexusbattles.ms_finanzas.creditos.domain.CuentaCredito;
import com.nexusbattles.ms_finanzas.creditos.domain.ReservaCredito;
import com.nexusbattles.ms_finanzas.creditos.dto.CreditoDTOs.*;
import com.nexusbattles.ms_finanzas.creditos.repository.CuentaCreditoRepository;
import com.nexusbattles.ms_finanzas.creditos.repository.ReservaCreditoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.UUID;
import com.nexusbattles.ms_finanzas.common.exception.ReservaNoEncontradaException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreditoServiceTest {

    @Mock
    private CuentaCreditoRepository cuentaRepository;
    @Mock
    private ReservaCreditoRepository reservaRepository;

    @InjectMocks
    private CreditoService creditoService;

    private CuentaCredito cuenta;

    @BeforeEach
    void setUp() {
        cuenta = CuentaCredito.builder()
            .jugadorUid("user-123")
            .saldoBruto(new BigDecimal("100.00"))
            .saldoReservado(BigDecimal.ZERO)
            .build();
    }

    @Test
    void obtenerSaldo_Exitoso() {
        // obtenerSaldo() usa el repositorio de solo lectura (findByJugadorUidReadOnly),
        // no el que tiene lock pesimista (findByJugadorUid) — ese es justo el fix
        // del bug del 500 permanente en GET /saldo.
        when(cuentaRepository.findByJugadorUidReadOnly("user-123")).thenReturn(Optional.of(cuenta));

        SaldoResponse response = creditoService.obtenerSaldo("user-123");

        assertNotNull(response);
        assertEquals(new BigDecimal("100.00"), response.saldoBruto());
        assertEquals(new BigDecimal("100.00"), response.saldoDisponible());
    }

    /** Como JPA: guardar una fila nueva le pone su id. */
    private UUID guardarConId() {
        UUID id = UUID.randomUUID();
        when(reservaRepository.save(any(ReservaCredito.class))).thenAnswer(invocacion -> {
            ReservaCredito fila = invocacion.getArgument(0);
            fila.setId(id);
            return fila;
        });
        return id;
    }

    @Test
    void acreditar_AumentaSaldoYPersiste() {
        // acreditar() registra la operación en reservaRepository, usando
        // idempotencyKey = refId (mismo patrón que debitar()), no un
        // repositorio de transacciones separado.
        when(cuentaRepository.findByJugadorUid("user-123")).thenReturn(Optional.of(cuenta));
        when(reservaRepository.findByIdempotencyKey("partida-001")).thenReturn(Optional.empty());
        UUID fila = guardarConId();

        AcreditarRequest req = new AcreditarRequest("user-123", new BigDecimal("2.00"), "partida-001", "recompensa-victoria");
        AcreditarResponse resp = creditoService.acreditar(req);

        assertEquals("APLICADO", resp.estado());
        assertEquals(new BigDecimal("102.00"), cuenta.getSaldoBruto());
        verify(reservaRepository, times(1)).save(any(ReservaCredito.class));
        // G7: creditos.yaml — TX-ACR- y los ocho primeros caracteres de la fila.
        assertEquals("TX-ACR-" + fila.toString().substring(0, 8).toUpperCase(), resp.transaccionId());
    }

    @Test
    void acreditar_EsIdempotente_NoDuplicaSiRefIdYaExiste() {
        // Si ya existe un registro con el mismo refId (reintento de red desde
        // el llamador), acreditar() no debe volver a sumar el monto.
        ReservaCredito operacionExistente = ReservaCredito.builder()
            .id(UUID.randomUUID())
            .jugadorUid("user-123")
            .monto(new BigDecimal("2.00"))
            .referenciaId("partida-001")
            .idempotencyKey("partida-001")
            .estado(ReservaCredito.EstadoReserva.CONSUMIDA)
            .build();

        when(reservaRepository.findByIdempotencyKey("partida-001")).thenReturn(Optional.of(operacionExistente));
        when(cuentaRepository.findByJugadorUid("user-123")).thenReturn(Optional.of(cuenta));

        AcreditarRequest req = new AcreditarRequest("user-123", new BigDecimal("2.00"), "partida-001", "recompensa-victoria");
        AcreditarResponse resp = creditoService.acreditar(req);

        assertEquals("APLICADO", resp.estado());
        assertEquals(new BigDecimal("100.00"), cuenta.getSaldoBruto()); // no cambió
        verify(reservaRepository, never()).save(any(ReservaCredito.class));
    }

    @Test
    void debitar_DescuentaSaldoYPersiste() {
        when(cuentaRepository.findByJugadorUid("user-123")).thenReturn(Optional.of(cuenta));
        when(reservaRepository.findByIdempotencyKey("sub-001")).thenReturn(Optional.empty());
        UUID fila = guardarConId();

        DebitarRequest req = new DebitarRequest("user-123", new BigDecimal("10.00"), "sub-001", "comision");
        DebitarResponse resp = creditoService.debitar(req);

        assertEquals("EXITOSO", resp.estado());
        assertEquals(new BigDecimal("90.00"), cuenta.getSaldoBruto());
        verify(reservaRepository, times(1)).save(any(ReservaCredito.class));
        // G7: creditos.yaml — TX-DEB- y los ocho primeros caracteres de la fila,
        // el mismo que devolverá cualquier reintento con ese refId.
        assertEquals("TX-DEB-" + fila.toString().substring(0, 8).toUpperCase(), resp.transaccionId());
    }

    private ReservaCredito debitoPrevio(String refId, ReservaCredito.EstadoReserva estado) {
        return ReservaCredito.builder()
            .id(UUID.randomUUID())
            .jugadorUid("user-123")
            .monto(new BigDecimal("10.00"))
            .referenciaId(refId)
            .idempotencyKey(refId)
            .estado(estado)
            .tipoOperacion(ReservaCredito.TipoOperacion.DEBITO)
            .build();
    }

    @Test
    void debitar_EsIdempotente_ElMismoRefIdNoDescuentaDosVeces() {
        ReservaCredito previo = debitoPrevio("orden-1", ReservaCredito.EstadoReserva.CONSUMIDA);
        when(reservaRepository.findByIdempotencyKey("orden-1")).thenReturn(Optional.of(previo));
        when(cuentaRepository.findByJugadorUid("user-123")).thenReturn(Optional.of(cuenta));

        DebitarResponse resp = creditoService.debitar(
            new DebitarRequest("user-123", new BigDecimal("10.00"), "orden-1", "compra"));

        assertEquals(new BigDecimal("100.00"), cuenta.getSaldoBruto());
        assertEquals("TX-DEB-" + previo.getId().toString().substring(0, 8).toUpperCase(), resp.transaccionId());
        verify(cuentaRepository, never()).save(any(CuentaCredito.class));
        verify(reservaRepository, never()).save(any(ReservaCredito.class));
    }

    @Test
    void debitar_ConRefIdYaReversado_NoVuelveADescontar() {
        // creditos.yaml 1.4.1 define esta convergencia: «si ya existe una
        // operacion registrada con ese refId, no se vuelve a descontar». Quien
        // quiere cobrar otra vez usa otro refId (ms-subastas, G7).
        ReservaCredito reversado = debitoPrevio("orden-2", ReservaCredito.EstadoReserva.LIBERADA);
        when(reservaRepository.findByIdempotencyKey("orden-2")).thenReturn(Optional.of(reversado));
        when(cuentaRepository.findByJugadorUid("user-123")).thenReturn(Optional.of(cuenta));

        creditoService.debitar(new DebitarRequest("user-123", new BigDecimal("10.00"), "orden-2", "compra"));

        assertEquals(new BigDecimal("100.00"), cuenta.getSaldoBruto());
        assertEquals(ReservaCredito.EstadoReserva.LIBERADA, reversado.getEstado());
        verify(reservaRepository, never()).save(any(ReservaCredito.class));
    }

    @Test
    void reversar_YaReversado_NoDevuelveOtraVez() {
        ReservaCredito reversado = debitoPrevio("orden-3", ReservaCredito.EstadoReserva.LIBERADA);
        when(reservaRepository.findByIdempotencyKey("orden-3")).thenReturn(Optional.of(reversado));

        ReversarResponse resp = creditoService.reversar(new ReversarRequest("orden-3", "otra vez"));

        assertEquals("YA_REVERSADO", resp.estado());
        verify(cuentaRepository, never()).save(any(CuentaCredito.class));
        verify(reservaRepository, never()).save(any(ReservaCredito.class));
    }
    @Test
    void reversar_RechazaSiNoEsDebito() {
        // FIX (reporte de Andrés): reversar() ya no debe aceptar una operación
        // que no sea un DEBITO real. Si el refId corresponde a una RESERVA de
        // puja activa (o a un CREDITO ya otorgado), debe rechazarla, no revertirla.
        ReservaCredito reservaDePuja = ReservaCredito.builder()
            .id(UUID.randomUUID())
            .jugadorUid("user-123")
            .monto(new BigDecimal("10.00"))
            .idempotencyKey("op-001")
            .estado(ReservaCredito.EstadoReserva.ACTIVA)
            .tipoOperacion(ReservaCredito.TipoOperacion.RESERVA)
            .build();

        when(reservaRepository.findByIdempotencyKey("op-001")).thenReturn(Optional.of(reservaDePuja));

        ReversarRequest req = new ReversarRequest("op-001", "prueba");

        assertThrows(ReservaNoEncontradaException.class, () -> creditoService.reversar(req));
        verify(cuentaRepository, never()).save(any(CuentaCredito.class));
    }

    @Test
    void reversar_ExitosoSiEsDebito() {
        // Un DEBITO real sí debe poder reversarse: se libera la operación y se
        // devuelve el monto al saldoBruto del jugador.
        ReservaCredito debitoOriginal = ReservaCredito.builder()
            .id(UUID.randomUUID())
            .jugadorUid("user-123")
            .monto(new BigDecimal("10.00"))
            .idempotencyKey("op-002")
            .estado(ReservaCredito.EstadoReserva.CONSUMIDA)
            .tipoOperacion(ReservaCredito.TipoOperacion.DEBITO)
            .build();

        when(reservaRepository.findByIdempotencyKey("op-002")).thenReturn(Optional.of(debitoOriginal));
        when(cuentaRepository.findByJugadorUid("user-123")).thenReturn(Optional.of(cuenta));

        ReversarRequest req = new ReversarRequest("op-002", "reembolso");
        ReversarResponse resp = creditoService.reversar(req);

        assertEquals("REVERSADO", resp.estado());
        assertEquals(new BigDecimal("110.00"), cuenta.getSaldoBruto());
        verify(reservaRepository, times(1)).save(any(ReservaCredito.class));
    }

    // ------------------------------------------------------------------
    // Historial de movimientos (#569)
    // ------------------------------------------------------------------

    /**
     * El signo lo decide el servicio una sola vez, y no la vista: es la regla
     * que hace que «apuesta liberada» no se pinte como un gasto.
     */
    @Test
    void elSignoDeCadaMovimientoSaleDelTipoYDelEstado() {
        assertEquals("SUMA", signoDe(ReservaCredito.TipoOperacion.CREDITO,
                ReservaCredito.EstadoReserva.CONSUMIDA));
        assertEquals("RESTA", signoDe(ReservaCredito.TipoOperacion.DEBITO,
                ReservaCredito.EstadoReserva.CONSUMIDA));
        assertEquals("RESTA", signoDe(ReservaCredito.TipoOperacion.RESERVA,
                ReservaCredito.EstadoReserva.CONSUMIDA));
        assertEquals("APARTA", signoDe(ReservaCredito.TipoOperacion.RESERVA,
                ReservaCredito.EstadoReserva.ACTIVA));
        // Una apuesta devuelta no movio el saldo: pintarla como gasto seria mentir.
        assertEquals("NEUTRO", signoDe(ReservaCredito.TipoOperacion.RESERVA,
                ReservaCredito.EstadoReserva.LIBERADA));
    }

    @Test
    void elHistorialDevuelveLoQueGuardaLaTablaDeOperaciones() {
        ReservaCredito operacion = ReservaCredito.builder()
                .jugadorUid("ana")
                .monto(new BigDecimal("60"))
                .concepto("apuesta-sala")
                .referenciaId("sala-1")
                .estado(ReservaCredito.EstadoReserva.LIBERADA)
                .tipoOperacion(ReservaCredito.TipoOperacion.RESERVA)
                .build();
        org.springframework.data.domain.Pageable pagina =
                org.springframework.data.domain.PageRequest.of(0, 20);
        when(reservaRepository.findByJugadorUidOrderByCreadoDesc("ana", pagina))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(operacion)));

        var resultado = creditoService.movimientos("ana", pagina);

        assertEquals(1, resultado.getTotalElements());
        MovimientoResponse linea = resultado.getContent().get(0);
        assertEquals("apuesta-sala", linea.concepto());
        assertEquals("sala-1", linea.referenciaId());
        assertEquals("RESERVA", linea.tipo());
        assertEquals("LIBERADA", linea.estado());
        assertEquals("NEUTRO", linea.signo());
    }

    // ------------------------------------------------------------------
    // creditos.yaml 1.4.1 (B7): el beneficiario de un consumo ve su ingreso
    // ------------------------------------------------------------------

    private ReservaCredito apuestaActiva(UUID id) {
        return ReservaCredito.builder()
                .id(id)
                .jugadorUid("perdedor")
                .monto(new BigDecimal("50.00"))
                .concepto("apuesta-sala")
                .referenciaId("sala-7-jugador-perdedor")
                .idempotencyKey("sala-7-jugador-perdedor-v1")
                .estado(ReservaCredito.EstadoReserva.ACTIVA)
                .tipoOperacion(ReservaCredito.TipoOperacion.RESERVA)
                .build();
    }

    @Test
    void consumir_ConBeneficiario_leDejaUnMovimientoDeCreditoASuNombre() {
        UUID id = UUID.randomUUID();
        ReservaCredito apuesta = apuestaActiva(id);
        CuentaCredito perdedor = CuentaCredito.builder().jugadorUid("perdedor")
                .saldoBruto(new BigDecimal("100.00")).saldoReservado(new BigDecimal("50.00")).build();
        CuentaCredito ganador = CuentaCredito.builder().jugadorUid("ganador")
                .saldoBruto(new BigDecimal("10.00")).saldoReservado(BigDecimal.ZERO).build();
        when(reservaRepository.findById(id)).thenReturn(Optional.of(apuesta));
        when(cuentaRepository.findByJugadorUid("perdedor")).thenReturn(Optional.of(perdedor));
        when(cuentaRepository.findByJugadorUid("ganador")).thenReturn(Optional.of(ganador));
        when(reservaRepository.findByIdempotencyKey("consumo-" + id)).thenReturn(Optional.empty());

        creditoService.consumir(id, new ConsumirRequest("ganador"));

        org.mockito.ArgumentCaptor<ReservaCredito> guardadas = org.mockito.ArgumentCaptor.forClass(ReservaCredito.class);
        verify(reservaRepository, times(2)).save(guardadas.capture());
        ReservaCredito ingreso = guardadas.getAllValues().get(0);
        assertEquals("ganador", ingreso.getJugadorUid());
        assertEquals(new BigDecimal("50.00"), ingreso.getMonto());
        assertEquals("cobro-de-reserva:apuesta-sala", ingreso.getConcepto());
        assertEquals("sala-7-jugador-perdedor", ingreso.getReferenciaId());
        assertEquals("consumo-" + id, ingreso.getIdempotencyKey());
        assertEquals(ReservaCredito.TipoOperacion.CREDITO, ingreso.getTipoOperacion());
        assertEquals("SUMA", CreditoService.comoMovimiento(ingreso).signo());
        assertEquals(new BigDecimal("60.00"), ganador.getSaldoBruto());
        assertEquals(ReservaCredito.EstadoReserva.CONSUMIDA, apuesta.getEstado());
    }

    @Test
    void consumir_SinBeneficiario_noDejaIngresoANadie() {
        UUID id = UUID.randomUUID();
        ReservaCredito apuesta = apuestaActiva(id);
        CuentaCredito perdedor = CuentaCredito.builder().jugadorUid("perdedor")
                .saldoBruto(new BigDecimal("100.00")).saldoReservado(new BigDecimal("50.00")).build();
        when(reservaRepository.findById(id)).thenReturn(Optional.of(apuesta));
        when(cuentaRepository.findByJugadorUid("perdedor")).thenReturn(Optional.of(perdedor));

        creditoService.consumir(id, new ConsumirRequest(null));

        verify(reservaRepository, times(1)).save(apuesta);
        verify(reservaRepository, never()).findByIdempotencyKey(any());
    }

    @Test
    void consumir_YaConsumida_noVuelveACobrarNiDuplicaElIngreso() {
        UUID id = UUID.randomUUID();
        ReservaCredito apuesta = apuestaActiva(id);
        apuesta.setEstado(ReservaCredito.EstadoReserva.CONSUMIDA);
        when(reservaRepository.findById(id)).thenReturn(Optional.of(apuesta));

        ConsumirResponse respuesta = creditoService.consumir(id, new ConsumirRequest("ganador"));

        assertEquals("TX-EXISTENTE", respuesta.transaccionId());
        verify(reservaRepository, never()).save(any(ReservaCredito.class));
    }

    @Test
    void consumir_ConElIngresoYaAnotado_noLoRepite() {
        UUID id = UUID.randomUUID();
        ReservaCredito apuesta = apuestaActiva(id);
        CuentaCredito perdedor = CuentaCredito.builder().jugadorUid("perdedor")
                .saldoBruto(new BigDecimal("100.00")).saldoReservado(new BigDecimal("50.00")).build();
        CuentaCredito ganador = CuentaCredito.builder().jugadorUid("ganador")
                .saldoBruto(BigDecimal.ZERO).saldoReservado(BigDecimal.ZERO).build();
        when(reservaRepository.findById(id)).thenReturn(Optional.of(apuesta));
        when(cuentaRepository.findByJugadorUid("perdedor")).thenReturn(Optional.of(perdedor));
        when(cuentaRepository.findByJugadorUid("ganador")).thenReturn(Optional.of(ganador));
        when(reservaRepository.findByIdempotencyKey("consumo-" + id))
                .thenReturn(Optional.of(ReservaCredito.builder().build()));

        creditoService.consumir(id, new ConsumirRequest("ganador"));

        verify(reservaRepository, times(1)).save(apuesta);
    }

    private static String signoDe(ReservaCredito.TipoOperacion tipo, ReservaCredito.EstadoReserva estado) {
        return CreditoService.comoMovimiento(ReservaCredito.builder()
                .jugadorUid("ana")
                .monto(BigDecimal.ONE)
                .tipoOperacion(tipo)
                .estado(estado)
                .build()).signo();
    }
}
