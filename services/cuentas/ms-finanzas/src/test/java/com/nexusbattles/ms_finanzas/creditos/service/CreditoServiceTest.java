package com.nexusbattles.ms_finanzas.creditos.service;

import com.nexusbattles.ms_finanzas.common.exception.ReservaYaLiberadaException;
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
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

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
        when(cuentaRepository.findByJugadorUidReadOnly("user-123")).thenReturn(Optional.of(cuenta));

        SaldoResponse response = creditoService.obtenerSaldo("user-123");

        assertNotNull(response);
        assertEquals(new BigDecimal("100.00"), response.saldoBruto());
    }

    @Test
    void acreditar_AumentaSaldoYPersiste() {
        when(cuentaRepository.findByJugadorUid("user-123")).thenReturn(Optional.of(cuenta));

        AcreditarRequest req = new AcreditarRequest("user-123", new BigDecimal("2.00"), "partida-001", "recompensa-victoria");
        AcreditarResponse resp = creditoService.acreditar(req);

        assertEquals("APLICADO", resp.estado());
        assertEquals(new BigDecimal("102.00"), cuenta.getSaldoBruto());
        verify(cuentaRepository, times(1)).save(any());
    }

    @Test
    void debitar_DescuentaSaldoYPersiste() {
        when(reservaRepository.findByIdempotencyKey("sub-001")).thenReturn(Optional.empty());
        when(cuentaRepository.findByJugadorUid("user-123")).thenReturn(Optional.of(cuenta));

        DebitarRequest req = new DebitarRequest("user-123", new BigDecimal("10.00"), "sub-001", "comision");
        DebitarResponse resp = creditoService.debitar(req);

        assertEquals("EXITOSO", resp.estado());
        assertEquals(new BigDecimal("90.00"), cuenta.getSaldoBruto());
        verify(cuentaRepository, times(1)).save(any());
    }

    // TEST DEFECTO 1: Validar que consumir() sobre una reserva LIBERADA lanza la excepción correcta y no altera el saldo
    @Test
    void consumir_ReservaLiberada_LanzaReservaYaLiberadaException() {
        // Arrange
        UUID reservaId = UUID.randomUUID();
        ReservaCredito reservaLiberada = ReservaCredito.builder()
            .id(reservaId)
            .estado(ReservaCredito.EstadoReserva.LIBERADA)
            .build();

        when(reservaRepository.findById(reservaId)).thenReturn(Optional.of(reservaLiberada));

        ConsumirRequest req = new ConsumirRequest("vendedor-123");

        // Act & Assert
        assertThrows(ReservaYaLiberadaException.class, () -> {
            creditoService.consumir(reservaId, req);
        });

        // Verificamos que NUNCA se llame a save() en la cuenta (no roba saldo)
        verify(cuentaRepository, never()).save(any());
    }

    // TEST DEFECTO 2: Validar el manejo de la condición de carrera (DataIntegrityViolationException) al crear cuenta
    @Test
    void obtenerOCrearCuenta_CondicionDeCarrera_RetornaCuentaExistente() {
        // Arrange
        String uid = "jugador-carrera";
        CuentaCredito cuentaExistente = CuentaCredito.builder()
            .jugadorUid(uid)
            .saldoBruto(BigDecimal.TEN)
            .saldoReservado(BigDecimal.ZERO)
            .build();

        when(cuentaRepository.findByJugadorUid(uid))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(cuentaExistente));

        when(cuentaRepository.save(any(CuentaCredito.class)))
            .thenThrow(new DataIntegrityViolationException("Llave duplicada"))
            .thenAnswer(invocation -> invocation.getArgument(0));

        AcreditarRequest req = new AcreditarRequest(uid, BigDecimal.ONE, "ref-1", "concepto");

        // Act & Assert
        assertDoesNotThrow(() -> {
            creditoService.acreditar(req);
        });

        verify(cuentaRepository, times(2)).findByJugadorUid(uid);
    }

    // TEST DEFECTO 3: Validar que obtenerSaldo llama al repositorio sin lock y no lanza excepción
    @Test
    void obtenerSaldo_CuentaExistente_RetornaSaldoCorrectamente() {
        // Arrange
        String uid = "jugador-saldo";
        CuentaCredito cuentaTest = CuentaCredito.builder()
            .jugadorUid(uid)
            .saldoBruto(new BigDecimal("150.00"))
            .saldoReservado(new BigDecimal("50.00"))
            .build();

        when(cuentaRepository.findByJugadorUidReadOnly(uid)).thenReturn(Optional.of(cuentaTest));

        // Act
        SaldoResponse response = creditoService.obtenerSaldo(uid);

        // Assert
        assertNotNull(response);
        assertEquals(new BigDecimal("150.00"), response.saldoBruto());
        assertEquals(new BigDecimal("50.00"), response.saldoReservado());
        assertEquals(new BigDecimal("100.00"), response.saldoDisponible());

        verify(cuentaRepository).findByJugadorUidReadOnly(uid);
    }
}
