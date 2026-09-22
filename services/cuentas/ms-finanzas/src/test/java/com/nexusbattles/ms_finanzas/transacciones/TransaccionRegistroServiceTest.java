package com.nexusbattles.ms_finanzas.transacciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransaccionRegistroServiceTest {

    private static final Instant AHORA = Instant.parse("2026-09-14T10:15:30Z");

    @Mock
    private TransaccionRepository repositorio;

    private TransaccionRegistroService servicio;

    @BeforeEach
    void setUp() {
        Clock reloj = Clock.fixed(AHORA, ZoneOffset.UTC);
        servicio = new TransaccionRegistroService(repositorio, reloj);
    }

    private RegistrarTransaccionRequest solicitudBase() {
        return new RegistrarTransaccionRequest(
                "ref-1", "uid-jugador-1", new BigDecimal("50000.00"),
                "cop", "compra-item-tienda",
                ResultadoTransaccion.APROBADO,
                "https://cdn/ejemplo.pdf", "pasarela-abc");
    }

    @Test
    void registrar_conRefIdNuevo_persistePasandoSolicitudYRelojAlEntidad() {
        when(repositorio.existsByRefId("ref-1")).thenReturn(false);
        when(repositorio.save(any(Transaccion.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaccion resultado = servicio.registrar(solicitudBase());

        ArgumentCaptor<Transaccion> captor = ArgumentCaptor.forClass(Transaccion.class);
        verify(repositorio).save(captor.capture());
        Transaccion persistida = captor.getValue();
        assertThat(persistida.getRefId()).isEqualTo("ref-1");
        assertThat(persistida.getUidUsuario()).isEqualTo("uid-jugador-1");
        assertThat(persistida.getMonto()).isEqualByComparingTo("50000.00");
        assertThat(persistida.getConcepto()).isEqualTo("compra-item-tienda");
        assertThat(persistida.getResultado()).isEqualTo(ResultadoTransaccion.APROBADO);
        assertThat(persistida.getComprobanteUrl()).isEqualTo("https://cdn/ejemplo.pdf");
        assertThat(persistida.getPasarelaRefExterna()).isEqualTo("pasarela-abc");
        assertThat(persistida.getCreado()).isEqualTo(AHORA);
        assertThat(persistida.getActualizado()).isEqualTo(AHORA);
        assertThat(resultado).isSameAs(persistida);
    }

    @Test
    void registrar_normalizaMonedaAMayusculas() {
        when(repositorio.existsByRefId(any())).thenReturn(false);
        when(repositorio.save(any(Transaccion.class))).thenAnswer(inv -> inv.getArgument(0));

        servicio.registrar(solicitudBase());

        ArgumentCaptor<Transaccion> captor = ArgumentCaptor.forClass(Transaccion.class);
        verify(repositorio).save(captor.capture());
        assertThat(captor.getValue().getMoneda()).isEqualTo("COP");
    }

    @Test
    void registrar_conRefIdExistente_lanzaExcepcionYNoLlamaSave() {
        when(repositorio.existsByRefId("ref-1")).thenReturn(true);

        assertThatThrownBy(() -> servicio.registrar(solicitudBase()))
                .isInstanceOf(TransaccionYaRegistradaException.class)
                .hasMessageContaining("ref-1");

        verify(repositorio, never()).save(any());
    }

    @Test
    void excepcionYaRegistrada_conservaRefId() {
        TransaccionYaRegistradaException ex = new TransaccionYaRegistradaException("ref-99");
        assertThat(ex.getRefId()).isEqualTo("ref-99");
        assertThat(ex.getMessage()).contains("ref-99");
    }

    @Test
    void buscarPorRefId_existente_delegaAlRepositorio() {
        Transaccion existente = new Transaccion();
        existente.setId(UUID.randomUUID());
        existente.setRefId("ref-1");
        when(repositorio.findByRefId("ref-1")).thenReturn(Optional.of(existente));

        Optional<Transaccion> resultado = servicio.buscarPorRefId("ref-1");

        assertThat(resultado).containsSame(existente);
    }

    @Test
    void buscarPorRefId_inexistente_retornaVacio() {
        when(repositorio.findByRefId("no-existe")).thenReturn(Optional.empty());
        assertThat(servicio.buscarPorRefId("no-existe")).isEmpty();
    }
}
