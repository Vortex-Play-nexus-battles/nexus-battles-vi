package com.nexusbattles.ms_chatbot.chat.analitica;

import com.nexusbattles.ms_chatbot.chat.analitica.AnaliticaChatbot.PuntoDeTendencia;
import com.nexusbattles.ms_chatbot.chat.model.Remitente;
import com.nexusbattles.ms_chatbot.chat.motor.model.TemaConocimiento;
import com.nexusbattles.ms_chatbot.chat.motor.repository.TemaConocimientoRepository;
import com.nexusbattles.ms_chatbot.chat.repository.CalificacionRepository;
import com.nexusbattles.ms_chatbot.chat.repository.MensajeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnaliticaChatbotServiceTest {

    private static final LocalDate DIA_1 = LocalDate.of(2026, 9, 9);
    private static final LocalDate DIA_2 = LocalDate.of(2026, 9, 10);

    @Mock
    private MensajeRepository mensajeRepository;
    @Mock
    private CalificacionRepository calificacionRepository;
    @Mock
    private TemaConocimientoRepository temaConocimientoRepository;

    private AnaliticaChatbotService servicio;

    @BeforeEach
    void configurar() {
        servicio = new AnaliticaChatbotService(mensajeRepository, calificacionRepository, temaConocimientoRepository);
    }

    @Test
    void calcular_resumeElPeriodo_conTasasYTemasFrecuentes() {
        UUID conversacionA = UUID.randomUUID();
        UUID conversacionB = UUID.randomUUID();
        when(mensajeRepository.buscarPreguntasEntre(eq(Remitente.USUARIO), any(), any())).thenReturn(List.of(
            new RegistroDePregunta(Instant.parse("2026-09-09T15:00:00Z"), conversacionA),
            new RegistroDePregunta(Instant.parse("2026-09-09T16:00:00Z"), conversacionA),
            new RegistroDePregunta(Instant.parse("2026-09-10T15:00:00Z"), conversacionB)));
        when(mensajeRepository.buscarRespuestasMedidasEntre(eq(Remitente.BOT), any(), any())).thenReturn(List.of(
            new RegistroDeRespuesta(Instant.parse("2026-09-09T15:00:01Z"), false, 100),
            new RegistroDeRespuesta(Instant.parse("2026-09-09T16:00:01Z"), false, 200),
            new RegistroDeRespuesta(Instant.parse("2026-09-10T15:00:01Z"), true, 300),
            new RegistroDeRespuesta(Instant.parse("2026-09-10T15:30:01Z"), false, null)));
        when(calificacionRepository.buscarUtilidadEntre(any(), any())).thenReturn(List.of(true, true, false));
        when(mensajeRepository.contarRespuestasPorTemaEntre(eq(Remitente.BOT), any(), any(), any()))
            .thenReturn(List.of(new ConteoDeTema("clave-registro", 3L)));
        TemaConocimiento tema = mock(TemaConocimiento.class);
        when(tema.getClave()).thenReturn("clave-registro");
        when(tema.getTitulo()).thenReturn("Como crear una cuenta");
        when(temaConocimientoRepository.findByClaveIn(any())).thenReturn(List.of(tema));

        AnaliticaChatbot analitica = servicio.calcular(DIA_1, DIA_2, AnaliticaChatbotService.ZONA_POR_DEFECTO);

        assertThat(analitica.conversaciones()).isEqualTo(2);
        assertThat(analitica.preguntas()).isEqualTo(3);
        assertThat(analitica.respuestasMedidas()).isEqualTo(4);
        assertThat(analitica.escalamientos()).isEqualTo(1);
        assertThat(analitica.tasaResolucion()).isEqualTo(0.75);
        assertThat(analitica.tiempoRespuestaPromedioMs()).isEqualTo(200.0); // el null no cuenta
        assertThat(analitica.satisfaccion()).isEqualTo(2.0 / 3);
        assertThat(analitica.temasFrecuentes()).singleElement()
            .satisfies(t -> {
                assertThat(t.titulo()).isEqualTo("Como crear una cuenta");
                assertThat(t.respuestas()).isEqualTo(3);
            });
    }

    // La tendencia trae un punto por cada dia, tambien los dias sin actividad.
    @Test
    void calcular_tendenciaIncluyeLosDiasSinActividad() {
        sinDatos();

        AnaliticaChatbot analitica = servicio.calcular(DIA_1, LocalDate.of(2026, 9, 12),
            AnaliticaChatbotService.ZONA_POR_DEFECTO);

        assertThat(analitica.tendencia()).hasSize(4)
            .allSatisfy(punto -> assertThat(punto.preguntas()).isZero());
    }

    // 2026-09-10T03:00Z son las 22:00 del 9 de septiembre en Bogota: cuenta
    // para el dia 9, no para el 10.
    @Test
    void calcular_agrupaLosDiasEnLaHoraDeColombia() {
        when(mensajeRepository.buscarPreguntasEntre(eq(Remitente.USUARIO), any(), any())).thenReturn(List.of(
            new RegistroDePregunta(Instant.parse("2026-09-10T03:00:00Z"), UUID.randomUUID())));
        when(mensajeRepository.buscarRespuestasMedidasEntre(eq(Remitente.BOT), any(), any())).thenReturn(List.of());
        when(calificacionRepository.buscarUtilidadEntre(any(), any())).thenReturn(List.of());
        when(mensajeRepository.contarRespuestasPorTemaEntre(eq(Remitente.BOT), any(), any(), any())).thenReturn(List.of());

        List<PuntoDeTendencia> tendencia = servicio.calcular(DIA_1, DIA_2,
            AnaliticaChatbotService.ZONA_POR_DEFECTO).tendencia();

        assertThat(tendencia.get(0).dia()).isEqualTo(DIA_1);
        assertThat(tendencia.get(0).preguntas()).isEqualTo(1);
        assertThat(tendencia.get(1).preguntas()).isZero();
    }

    // "Sin datos" es null, no 0 %: un tablero vacio no debe decir que la
    // satisfaccion es cero.
    @Test
    void calcular_sinDatos_dejaLasTasasEnNull() {
        sinDatos();

        AnaliticaChatbot analitica = servicio.calcular(DIA_1, DIA_1, AnaliticaChatbotService.ZONA_POR_DEFECTO);

        assertThat(analitica.tasaResolucion()).isNull();
        assertThat(analitica.satisfaccion()).isNull();
        assertThat(analitica.tiempoRespuestaPromedioMs()).isNull();
        assertThat(analitica.temasFrecuentes()).isEmpty();
    }

    @Test
    void calcular_hastaAnteriorADesde_lanza400() {
        assertThatThrownBy(() -> servicio.calcular(DIA_2, DIA_1, AnaliticaChatbotService.ZONA_POR_DEFECTO))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void calcular_periodoDemasiadoLargo_lanza400() {
        assertThatThrownBy(() -> servicio.calcular(DIA_1, DIA_1.plusDays(AnaliticaChatbotService.MAXIMO_DIAS),
            AnaliticaChatbotService.ZONA_POR_DEFECTO))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private void sinDatos() {
        when(mensajeRepository.buscarPreguntasEntre(eq(Remitente.USUARIO), any(), any())).thenReturn(List.of());
        when(mensajeRepository.buscarRespuestasMedidasEntre(eq(Remitente.BOT), any(), any())).thenReturn(List.of());
        when(calificacionRepository.buscarUtilidadEntre(any(), any())).thenReturn(List.of());
        when(mensajeRepository.contarRespuestasPorTemaEntre(eq(Remitente.BOT), any(), any(), any())).thenReturn(List.of());
    }
}
