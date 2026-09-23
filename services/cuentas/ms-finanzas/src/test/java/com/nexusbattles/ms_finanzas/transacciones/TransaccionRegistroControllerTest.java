package com.nexusbattles.ms_finanzas.transacciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class TransaccionRegistroControllerTest {

    @Mock
    private TransaccionRegistroService servicio;

    @InjectMocks
    private TransaccionRegistroController controller;

    private RegistrarTransaccionApiRequest peticion() {
        return new RegistrarTransaccionApiRequest(
                "ref-1", "uid-jugador-1", new BigDecimal("50000.00"),
                "cop", "compra-item-tienda", ResultadoTransaccion.APROBADO,
                "https://cdn/ejemplo.pdf", "pasarela-abc");
    }

    private Transaccion persistida() {
        Transaccion t = new Transaccion();
        t.setId(UUID.randomUUID());
        t.setRefId("ref-1");
        t.setUidUsuario("uid-jugador-1");
        t.setMonto(new BigDecimal("50000.00"));
        t.setMoneda("COP");
        t.setConcepto("compra-item-tienda");
        t.setResultado(ResultadoTransaccion.APROBADO);
        t.setComprobanteUrl("https://cdn/ejemplo.pdf");
        t.setPasarelaRefExterna("pasarela-abc");
        t.setCreado(Instant.parse("2026-09-22T10:00:00Z"));
        t.setActualizado(Instant.parse("2026-09-22T10:00:00Z"));
        return t;
    }

    @Test
    void registrar_delegaAlServicioYDevuelve201ConElResumen() {
        when(servicio.registrar(any(RegistrarTransaccionRequest.class))).thenReturn(persistida());

        ResponseEntity<ResumenTransaccion> respuesta = controller.registrar(peticion());

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().refId()).isEqualTo("ref-1");
        assertThat(respuesta.getBody().resultado()).isEqualTo(ResultadoTransaccion.APROBADO);
        // pasarelaRefExterna es un detalle interno de conciliacion: ResumenTransaccion
        // (el mismo DTO de lectura del historial) no lo expone.
    }

    @Test
    void registrar_traduceLaPeticionApiALaInternaSinPerderCampos() {
        when(servicio.registrar(any(RegistrarTransaccionRequest.class))).thenReturn(persistida());

        controller.registrar(peticion());

        ArgumentCaptor<RegistrarTransaccionRequest> captor =
                ArgumentCaptor.forClass(RegistrarTransaccionRequest.class);
        verify(servicio).registrar(captor.capture());
        RegistrarTransaccionRequest enviada = captor.getValue();
        assertThat(enviada.refId()).isEqualTo("ref-1");
        assertThat(enviada.uidUsuario()).isEqualTo("uid-jugador-1");
        assertThat(enviada.monto()).isEqualByComparingTo("50000.00");
        // La normalizacion a mayusculas la hace TransaccionRegistroService, no
        // el controller: aqui solo importa que el valor crudo llegue intacto.
        assertThat(enviada.moneda()).isEqualTo("cop");
        assertThat(enviada.concepto()).isEqualTo("compra-item-tienda");
        assertThat(enviada.resultado()).isEqualTo(ResultadoTransaccion.APROBADO);
        assertThat(enviada.comprobanteUrl()).isEqualTo("https://cdn/ejemplo.pdf");
        assertThat(enviada.pasarelaRefExterna()).isEqualTo("pasarela-abc");
    }
}
