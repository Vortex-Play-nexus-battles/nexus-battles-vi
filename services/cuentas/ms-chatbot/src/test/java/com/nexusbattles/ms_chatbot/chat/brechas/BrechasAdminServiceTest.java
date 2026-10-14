package com.nexusbattles.ms_chatbot.chat.brechas;

import com.nexusbattles.ms_chatbot.chat.analitica.PeriodoDelPanel;
import com.nexusbattles.ms_chatbot.chat.model.BrechaConocimiento;
import com.nexusbattles.ms_chatbot.chat.model.EstadoBrecha;
import com.nexusbattles.ms_chatbot.chat.repository.BrechaConocimientoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// ms-chatbot.yaml 1.7.0: el administrador lista y atiende las preguntas sin respuesta.
@ExtendWith(MockitoExtension.class)
class BrechasAdminServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-14T15:00:00Z");
    private static final PeriodoDelPanel SEPTIEMBRE =
        new PeriodoDelPanel(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

    @Mock
    private BrechaConocimientoRepository brechas;

    private BrechasAdminService servicio;

    @BeforeEach
    void configurar() {
        servicio = new BrechasAdminService(brechas, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    // Medianoche del 1 de septiembre y del 1 de octubre en Bogota (UTC-5).
    @Test
    void listarSinEstado_pideTodasLasDelPeriodoEnHoraDeColombia() {
        Page<BrechaConocimiento> pagina = new PageImpl<>(List.of());
        when(brechas.buscarConActividadEntre(eq(Instant.parse("2026-09-01T05:00:00Z")),
            eq(Instant.parse("2026-10-01T05:00:00Z")), any(Pageable.class))).thenReturn(pagina);

        assertThat(servicio.listar(SEPTIEMBRE, null, 2, 16)).isSameAs(pagina);

        verify(brechas).buscarConActividadEntre(any(Instant.class), any(Instant.class), eq(PageRequest.of(2, 16)));
    }

    @Test
    void listarConEstado_filtraYAcotaElTamano() {
        Page<BrechaConocimiento> pagina = new PageImpl<>(List.of());
        when(brechas.buscarConActividadEntre(eq(EstadoBrecha.PENDIENTE), any(), any(), any())).thenReturn(pagina);

        assertThat(servicio.listar(SEPTIEMBRE, EstadoBrecha.PENDIENTE, -3, 500)).isSameAs(pagina);

        verify(brechas).buscarConActividadEntre(eq(EstadoBrecha.PENDIENTE), any(), any(),
            eq(PageRequest.of(0, BrechasAdminService.TAMANO_MAXIMO)));
    }

    @Test
    void unPeriodoInvalidoEs400SinConsultar() {
        PeriodoDelPanel alReves = new PeriodoDelPanel(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1));

        assertThatThrownBy(() -> servicio.listar(alReves, null, 0, 20))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(brechas);
    }

    @Test
    void cambiarEstado_guardaQuienYCuandoConLaHoraDelReloj() {
        BrechaConocimiento brecha = new BrechaConocimiento("como gano gemas", "¿Cómo gano gemas?");
        UUID id = UUID.randomUUID();
        when(brechas.findById(id)).thenReturn(Optional.of(brecha));
        when(brechas.save(brecha)).thenReturn(brecha);

        BrechaConocimiento resultado = servicio.cambiarEstado(id, EstadoBrecha.ATENDIDA, "uid-admin");

        assertThat(resultado.getEstado()).isEqualTo(EstadoBrecha.ATENDIDA);
        assertThat(resultado.getEstadoCambiadoPor()).isEqualTo("uid-admin");
        assertThat(resultado.getEstadoCambiadoEn()).isEqualTo(AHORA);
    }

    @Test
    void cambiarAlMismoEstado_noGuarda() {
        BrechaConocimiento brecha = new BrechaConocimiento("como gano gemas", "¿Cómo gano gemas?");
        UUID id = UUID.randomUUID();
        when(brechas.findById(id)).thenReturn(Optional.of(brecha));

        assertThat(servicio.cambiarEstado(id, EstadoBrecha.PENDIENTE, "uid-admin")).isSameAs(brecha);

        verify(brechas, never()).save(any());
    }

    @Test
    void unaBrechaQueNoExisteEs404() {
        UUID id = UUID.randomUUID();
        when(brechas.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.cambiarEstado(id, EstadoBrecha.ATENDIDA, "uid-admin"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
