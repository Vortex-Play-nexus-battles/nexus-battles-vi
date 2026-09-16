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
}
