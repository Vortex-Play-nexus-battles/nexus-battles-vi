package com.nexusbattles.ms_finanzas.pagos.service;

import com.nexusbattles.ms_finanzas.pagos.correo.ConfirmacionCompraClient;
import com.nexusbattles.ms_finanzas.pagos.dto.PagoDTOs.ProcesarPagoRequest;
import com.nexusbattles.ms_finanzas.pagos.dto.PagoDTOs.ProcesarPagoResponse;
import com.nexusbattles.ms_finanzas.pagos.pasarela.PasarelaSimuladaClient;
import com.nexusbattles.ms_finanzas.transacciones.RegistrarTransaccionRequest;
import com.nexusbattles.ms_finanzas.transacciones.ResultadoTransaccion;
import com.nexusbattles.ms_finanzas.transacciones.Transaccion;
import com.nexusbattles.ms_finanzas.transacciones.TransaccionRegistroService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PagoServiceTest {

    private final PasarelaSimuladaClient pasarelaReal = new PasarelaSimuladaClient();
    private final TransaccionRegistroService transaccionesMock = mock(TransaccionRegistroService.class);
    private final ConfirmacionCompraClient correoMock = mock(ConfirmacionCompraClient.class);
    private final PagoService pagoService = new PagoService(pasarelaReal, transaccionesMock, correoMock);

    @Test
    void procesar_MontoValido_QuedaAprobadoYRegistrado() {
        when(transaccionesMock.buscarPorRefId("refId-aprobado-1")).thenReturn(Optional.empty());
        when(transaccionesMock.registrar(any(RegistrarTransaccionRequest.class)))
            .thenReturn(mock(Transaccion.class));

        ProcesarPagoRequest req = new ProcesarPagoRequest(
            "user-1", new BigDecimal("50.00"), "COP", "compra de prueba", "refId-aprobado-1"
        );

        ProcesarPagoResponse resp = pagoService.procesar(req);

        assertEquals("APROBADO", resp.estado());
        assertFalse(resp.marcadoParaRevisionManual());
        verify(transaccionesMock, times(1)).registrar(any(RegistrarTransaccionRequest.class));
        verify(correoMock, times(1)).enviarConfirmacionCompra(any());
    }

    @Test
    void procesar_MontoCeroOMenor_QuedaRechazado() {
        when(transaccionesMock.buscarPorRefId("refId-rechazado-1")).thenReturn(Optional.empty());

        ProcesarPagoRequest req = new ProcesarPagoRequest(
            "user-1", BigDecimal.ZERO, "COP", "compra inválida", "refId-rechazado-1"
        );

        ProcesarPagoResponse resp = pagoService.procesar(req);

        assertEquals("RECHAZADO", resp.estado());
        assertEquals("Monto inválido", resp.mensaje());
        verify(correoMock, never()).enviarConfirmacionCompra(any());
    }

    @Test
    void procesar_MontoSuperaUmbral_QuedaMarcadoParaRevisionManual() {
        when(transaccionesMock.buscarPorRefId("refId-alto-valor-1")).thenReturn(Optional.empty());
        when(transaccionesMock.registrar(any(RegistrarTransaccionRequest.class)))
            .thenReturn(mock(Transaccion.class));

        ProcesarPagoRequest req = new ProcesarPagoRequest(
            "user-1", new BigDecimal("5000.00"), "COP", "compra grande", "refId-alto-valor-1"
        );

        ProcesarPagoResponse resp = pagoService.procesar(req);

        assertEquals("APROBADO", resp.estado());
        assertTrue(resp.marcadoParaRevisionManual());
    }

    @Test
    void procesar_PasarelaFallaSiempre_QuedaIndeterminadoTrasAgotarReintentos() {
        when(transaccionesMock.buscarPorRefId("refId-FALLO-PASARELA-1")).thenReturn(Optional.empty());
        when(transaccionesMock.registrar(any(RegistrarTransaccionRequest.class)))
            .thenReturn(mock(Transaccion.class));

        ProcesarPagoRequest req = new ProcesarPagoRequest(
            "user-1", new BigDecimal("50.00"), "COP", "compra con fallo", "refId-FALLO-PASARELA-1"
        );

        ProcesarPagoResponse resp = pagoService.procesar(req);

        assertEquals("INDETERMINADO", resp.estado());
        assertTrue(resp.mensaje().contains("conciliación"));
        verify(transaccionesMock, times(1)).registrar(any(RegistrarTransaccionRequest.class));
        verify(correoMock, never()).enviarConfirmacionCompra(any());
    }

    /**
     * Reproduce el hallazgo de Julián (23-sep): un reintento del llamador
     * con el mismo refId no debe repetir NADA — ni la pasarela, ni el
     * registro, ni (sobre todo) el correo de confirmación.
     */
    @Test
    void procesar_RefIdYaRegistrado_NoRepiteNiPasarelaNiCorreo() {
        Transaccion transaccionExistente = mock(Transaccion.class);
        when(transaccionExistente.getResultado()).thenReturn(ResultadoTransaccion.APROBADO);
        when(transaccionesMock.buscarPorRefId("refId-repetido-1")).thenReturn(Optional.of(transaccionExistente));

        ProcesarPagoRequest req = new ProcesarPagoRequest(
            "user-1", new BigDecimal("50.00"), "COP", "compra de prueba", "refId-repetido-1"
        );

        ProcesarPagoResponse resp = pagoService.procesar(req);

        assertEquals("APROBADO", resp.estado());
        assertEquals("refId-repetido-1", resp.refId());
        verify(transaccionesMock, never()).registrar(any(RegistrarTransaccionRequest.class));
        verify(correoMock, never()).enviarConfirmacionCompra(any());
    }
}
