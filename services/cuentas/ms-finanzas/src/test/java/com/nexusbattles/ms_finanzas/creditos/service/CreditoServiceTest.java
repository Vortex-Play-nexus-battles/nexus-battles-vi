package com.nexusbattles.ms_finanzas.creditos.service;

import com.nexusbattles.ms_finanzas.creditos.domain.CuentaCredito;
import com.nexusbattles.ms_finanzas.creditos.dto.CreditoDTOs.*;
import com.nexusbattles.ms_finanzas.creditos.repository.CuentaCreditoRepository;
import com.nexusbattles.ms_finanzas.creditos.repository.ReservaCreditoRepository;
import com.nexusbattles.ms_finanzas.creditos.repository.TransaccionCreditoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    @Mock
    private TransaccionCreditoRepository transaccionRepository;

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
        when(cuentaRepository.findByJugadorUid("user-123")).thenReturn(Optional.of(cuenta));

        SaldoResponse response = creditoService.obtenerSaldo("user-123");

        assertNotNull(response);
        assertEquals(new BigDecimal("100.00"), response.saldoBruto());
    }

    @Test
    void acreditar_AumentaSaldoYPersiste() {
        when(cuentaRepository.findByJugadorUid("user-123")).thenReturn(Optional.of(cuenta));
        when(transaccionRepository.findByRefId("partida-001")).thenReturn(Optional.empty());

        AcreditarRequest req = new AcreditarRequest("user-123", new BigDecimal("2.00"), "partida-001", "recompensa-victoria");
        AcreditarResponse resp = creditoService.acreditar(req);

        assertEquals("APLICADO", resp.estado());
        assertEquals(new BigDecimal("102.00"), cuenta.getSaldoBruto());
        verify(transaccionRepository, times(1)).save(any());
    }

    @Test
    void debitar_DescuentaSaldoYPersiste() {
        when(cuentaRepository.findByJugadorUid("user-123")).thenReturn(Optional.of(cuenta));
        when(transaccionRepository.findByRefId("sub-001")).thenReturn(Optional.empty());

        DebitarRequest req = new DebitarRequest("user-123", new BigDecimal("10.00"), "sub-001", "comision");
        DebitarResponse resp = creditoService.debitar(req);

        assertEquals("EXITOSO", resp.estado());
        assertEquals(new BigDecimal("90.00"), cuenta.getSaldoBruto());
        verify(transaccionRepository, times(1)).save(any());
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

        // 1. La primera vez que busca, no la encuentra (empty).
        // 2. La segunda vez que busca (dentro del catch), sí la encuentra.
        when(cuentaRepository.findByJugadorUid(uid))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(cuentaExistente));

        // Simulamos que al intentar guardar, otro hilo nos ganó y la BD lanza la excepción de llave duplicada
        when(cuentaRepository.save(any(CuentaCredito.class)))
            .thenThrow(new DataIntegrityViolationException("Llave duplicada"));

        // Act
        // Usamos acreditar() porque es un método público que llama a obtenerOCrearCuenta()
        AcreditarRequest req = new AcreditarRequest(uid, BigDecimal.ONE, "ref-1", "concepto");

        // Assert: No debe reventar, debe atrapar el error, buscar de nuevo y sumar el saldo a la cuenta recuperada
        assertDoesNotThrow(() -> {
            creditoService.acreditar(req);
        });

        // Verificamos que intentó buscar la cuenta 2 veces (antes del try, y dentro del catch)
        verify(cuentaRepository, times(2)).findByJugadorUid(uid);
    }

    // TEST DEFECTO 3: Validar que obtenerSaldo llama al repositorio sin lock y no lanza excepción
    @Test
    void obtenerSaldo_CuentaExistente_RetornaSaldoCorrectamente() {
        // Arrange
        String uid = "jugador-saldo";
        CuentaCredito cuenta = CuentaCredito.builder()
            .jugadorUid(uid)
            .saldoBruto(new BigDecimal("150.00"))
            .saldoReservado(new BigDecimal("50.00"))
            .build();

        when(cuentaRepository.findByJugadorUidReadOnly(uid)).thenReturn(Optional.of(cuenta));

        // Act
        SaldoResponse response = creditoService.obtenerSaldo(uid);

        // Assert
        assertNotNull(response);
        assertEquals(new BigDecimal("150.00"), response.saldoBruto());
        assertEquals(new BigDecimal("50.00"), response.saldoReservado());
        assertEquals(new BigDecimal("100.00"), response.saldoDisponible());

        // Verificar que llamó al método seguro de solo lectura
        verify(cuentaRepository).findByJugadorUidReadOnly(uid);
    }
}
