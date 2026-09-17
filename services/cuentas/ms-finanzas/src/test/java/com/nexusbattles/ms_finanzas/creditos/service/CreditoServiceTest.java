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

    @Test
    void acreditar_AumentaSaldoYPersiste() {
        // acreditar() registra la operación en reservaRepository, usando
        // idempotencyKey = refId (mismo patrón que debitar()), no un
        // repositorio de transacciones separado.
        when(cuentaRepository.findByJugadorUid("user-123")).thenReturn(Optional.of(cuenta));
        when(reservaRepository.findByIdempotencyKey("partida-001")).thenReturn(Optional.empty());

        AcreditarRequest req = new AcreditarRequest("user-123", new BigDecimal("2.00"), "partida-001", "recompensa-victoria");
        AcreditarResponse resp = creditoService.acreditar(req);

        assertEquals("APLICADO", resp.estado());
        assertEquals(new BigDecimal("102.00"), cuenta.getSaldoBruto());
        verify(reservaRepository, times(1)).save(any(ReservaCredito.class));
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

        DebitarRequest req = new DebitarRequest("user-123", new BigDecimal("10.00"), "sub-001", "comision");
        DebitarResponse resp = creditoService.debitar(req);

        assertEquals("EXITOSO", resp.estado());
        assertEquals(new BigDecimal("90.00"), cuenta.getSaldoBruto());
        verify(reservaRepository, times(1)).save(any(ReservaCredito.class));
    }
}
