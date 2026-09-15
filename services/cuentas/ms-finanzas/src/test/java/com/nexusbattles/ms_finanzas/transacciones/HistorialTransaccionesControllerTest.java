package com.nexusbattles.ms_finanzas.transacciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import com.nexusbattles.ms_finanzas.seguridad.SecurityInterceptor;

@ExtendWith(MockitoExtension.class)
class HistorialTransaccionesControllerTest {

    @Mock
    private TransaccionConsultaService consultaService;

    @InjectMocks
    private HistorialTransaccionesController controller;

    private ResumenTransaccion resumen() {
        return new ResumenTransaccion(
                UUID.randomUUID(), "ref-1", new BigDecimal("100.00"),
                "COP", "compra-item", ResultadoTransaccion.APROBADO,
                null, Instant.parse("2026-09-15T10:00:00Z"));
    }

    @Test
    void miHistorial_usaUidDelAtributoYDelegaAlService() {
        String uid = UUID.randomUUID().toString();
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute(SecurityInterceptor.ATTR_UID, uid);

        Page<ResumenTransaccion> pagina = new PageImpl<>(
                List.of(resumen()), PageRequest.of(0, 20), 1);
        when(consultaService.listarPorUsuario(eq(uid), eq(PageRequest.of(0, 20))))
                .thenReturn(pagina);

        ResponseEntity<Page<ResumenTransaccion>> respuesta = controller.miHistorial(req, 0, 20);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().getTotalElements()).isEqualTo(1);
    }

    @Test
    void miHistorial_tamanoPaginaMayorAlTope_seCapa() {
        String uid = UUID.randomUUID().toString();
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute(SecurityInterceptor.ATTR_UID, uid);

        when(consultaService.listarPorUsuario(eq(uid), eq(PageRequest.of(0, 100))))
                .thenReturn(Page.empty(PageRequest.of(0, 100)));

        controller.miHistorial(req, 0, 999_999);

        // Si el mock coincidió con PageRequest.of(0, 100), la aserción está
        // en el when(): significa que el controller cortó el size al tope.
    }

    @Test
    void miHistorial_paginaNegativa_seNormalizaACero() {
        String uid = UUID.randomUUID().toString();
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute(SecurityInterceptor.ATTR_UID, uid);

        when(consultaService.listarPorUsuario(eq(uid), eq(PageRequest.of(0, 20))))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        controller.miHistorial(req, -5, 20);
    }

    @Test
    void miHistorial_tamanoPaginaCero_seNormalizaAUno() {
        String uid = UUID.randomUUID().toString();
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute(SecurityInterceptor.ATTR_UID, uid);

        Pageable esperado = PageRequest.of(0, 1);
        when(consultaService.listarPorUsuario(eq(uid), eq(esperado)))
                .thenReturn(Page.empty(esperado));

        controller.miHistorial(req, 0, 0);
    }
}
