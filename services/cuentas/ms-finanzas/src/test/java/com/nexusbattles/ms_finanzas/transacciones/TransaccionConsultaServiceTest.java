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

@ExtendWith(MockitoExtension.class)
class TransaccionConsultaServiceTest {

    @Mock
    private TransaccionRepository repositorio;

    @InjectMocks
    private TransaccionConsultaService servicio;

    private Transaccion crear(String refId, String concepto) {
        Transaccion t = new Transaccion();
        t.setId(UUID.randomUUID());
        t.setRefId(refId);
        t.setUidUsuario("uid-1");
        t.setMonto(new BigDecimal("100.00"));
        t.setMoneda("COP");
        t.setConcepto(concepto);
        t.setResultado(ResultadoTransaccion.APROBADO);
        t.setComprobanteUrl("https://cdn/" + refId + ".pdf");
        t.setPasarelaRefExterna("no-visible-al-cliente");
        t.setCreado(Instant.parse("2026-09-14T10:00:00Z"));
        t.setActualizado(Instant.parse("2026-09-14T10:00:00Z"));
        return t;
    }

    @Test
    void listarPorUsuario_retornaPaginaMapeadaAResumenes() {
        Pageable pagina = PageRequest.of(0, 20);
        Transaccion t1 = crear("ref-1", "compra-item");
        Transaccion t2 = crear("ref-2", "recarga-creditos");
        Page<Transaccion> pagenDominio = new PageImpl<>(List.of(t1, t2), pagina, 2);
        when(repositorio.findByUidUsuarioOrderByCreadoDesc(eq("uid-1"), eq(pagina)))
                .thenReturn(pagenDominio);

        Page<ResumenTransaccion> resultado = servicio.listarPorUsuario("uid-1", pagina);

        assertThat(resultado.getContent()).hasSize(2);
        assertThat(resultado.getContent().get(0).refId()).isEqualTo("ref-1");
        assertThat(resultado.getContent().get(0).concepto()).isEqualTo("compra-item");
        // El resumen NO expone la referencia externa de la pasarela: es detalle
        // interno de conciliación, no debe llegar al historial del jugador.
        assertThat(resultado.getContent().get(0))
                .extracting(ResumenTransaccion::refId, ResumenTransaccion::moneda,
                        ResumenTransaccion::resultado)
                .containsExactly("ref-1", "COP", ResultadoTransaccion.APROBADO);
    }

    @Test
    void listarPorUsuario_sinResultados_retornaPaginaVacia() {
        Pageable pagina = PageRequest.of(0, 20);
        when(repositorio.findByUidUsuarioOrderByCreadoDesc(eq("uid-nadie"), eq(pagina)))
                .thenReturn(Page.empty(pagina));

        Page<ResumenTransaccion> resultado = servicio.listarPorUsuario("uid-nadie", pagina);

        assertThat(resultado.getContent()).isEmpty();
        assertThat(resultado.getTotalElements()).isZero();
    }
}
