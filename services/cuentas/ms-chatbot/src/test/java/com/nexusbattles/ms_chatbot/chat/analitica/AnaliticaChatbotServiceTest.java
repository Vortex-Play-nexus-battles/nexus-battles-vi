package com.nexusbattles.ms_chatbot.chat.analitica;

import com.nexusbattles.ms_chatbot.chat.analitica.AnaliticaChatbot.PuntoDeTendencia;
import com.nexusbattles.ms_chatbot.chat.analitica.AnaliticaChatbot.RespuestasPorResultado;
import com.nexusbattles.ms_chatbot.chat.analitica.AnaliticaChatbot.TendenciaDePalabra;
import com.nexusbattles.ms_chatbot.chat.model.Remitente;
import com.nexusbattles.ms_chatbot.chat.model.ResultadoDeRespuesta;
import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.motor.model.TemaConocimiento;
import com.nexusbattles.ms_chatbot.chat.motor.repository.TemaConocimientoRepository;
import com.nexusbattles.ms_chatbot.chat.repository.CalificacionRepository;
import com.nexusbattles.ms_chatbot.chat.repository.ConversacionRepository;
import com.nexusbattles.ms_chatbot.chat.repository.MensajeRepository;
import com.nexusbattles.ms_chatbot.chat.soporte.EstadoTicket;
import com.nexusbattles.ms_chatbot.chat.soporte.RegistroDeTicket;
import com.nexusbattles.ms_chatbot.chat.soporte.TicketSoporteRepository;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnaliticaChatbotServiceTest {

    private static final LocalDate DIA_1 = LocalDate.of(2026, 9, 9);
    private static final LocalDate DIA_2 = LocalDate.of(2026, 9, 10);

    // Medianoche del 9 y del 11 de septiembre en Bogota (UTC-5).
    private static final Instant INICIO = Instant.parse("2026-09-09T05:00:00Z");
    private static final Instant FIN = Instant.parse("2026-09-11T05:00:00Z");
    // El periodo anterior de los mismos dos dias: 7 y 8 de septiembre.
    private static final Instant INICIO_ANTERIOR = Instant.parse("2026-09-07T05:00:00Z");

    @Mock
    private MensajeRepository mensajeRepository;
    @Mock
    private CalificacionRepository calificacionRepository;
    @Mock
    private TemaConocimientoRepository temaConocimientoRepository;
    @Mock
    private TicketSoporteRepository ticketSoporteRepository;
    @Mock
    private ConversacionRepository conversacionRepository;

    private AnaliticaChatbotService servicio;

    @BeforeEach
    void configurar() {
        servicio = new AnaliticaChatbotService(mensajeRepository, calificacionRepository, temaConocimientoRepository,
            ticketSoporteRepository, conversacionRepository);
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
            new RegistroDeRespuesta(Instant.parse("2026-09-09T15:00:01Z"), false, 100, ResultadoDeRespuesta.TEMA),
            new RegistroDeRespuesta(Instant.parse("2026-09-09T16:00:01Z"), false, 200, ResultadoDeRespuesta.TEMA),
            new RegistroDeRespuesta(Instant.parse("2026-09-10T15:00:01Z"), true, 300, ResultadoDeRespuesta.ESCALADO),
            new RegistroDeRespuesta(Instant.parse("2026-09-10T15:30:01Z"), false, null,
                ResultadoDeRespuesta.CONSULTA_EN_VIVO)));
        when(calificacionRepository.buscarUtilidadEntre(any(), any())).thenReturn(List.of(true, true, false));
        when(mensajeRepository.contarRespuestasPorTemaEntre(eq(Remitente.BOT), any(), any(), any()))
            .thenReturn(List.of(new ConteoDeTema("clave-registro", 3L)));
        TemaConocimiento tema = mock(TemaConocimiento.class);
        when(tema.getClave()).thenReturn("clave-registro");
        when(tema.getTitulo()).thenReturn("Como crear una cuenta");
        when(temaConocimientoRepository.findByClaveIn(any())).thenReturn(List.of(tema));

        AnaliticaChatbot analitica = servicio.calcular(DIA_1, DIA_2, AnaliticaChatbotService.ZONA_POR_DEFECTO);

        assertThat(analitica.conversacionesConActividad()).isEqualTo(2);
        assertThat(analitica.preguntas()).isEqualTo(3);
        assertThat(analitica.respuestasMedidas()).isEqualTo(4);
        assertThat(analitica.escalamientos()).isEqualTo(1);
        assertThat(analitica.respuestasClasificadas()).isEqualTo(4);
        assertThat(analitica.respuestasResueltas()).isEqualTo(3);
        assertThat(analitica.tasaResolucion()).isEqualTo(0.75);
        assertThat(analitica.tiempoRespuestaPromedioMs()).isEqualTo(200.0); // el null no cuenta
        assertThat(analitica.satisfaccion()).isEqualTo(2.0 / 3);
        assertThat(analitica.temasFrecuentes()).singleElement()
            .satisfies(t -> {
                assertThat(t.titulo()).isEqualTo("Como crear una cuenta");
                assertThat(t.respuestas()).isEqualTo(3);
            });
    }

    // 1.7.0: solo resuelven TEMA, CONSULTA_EN_VIVO y NAVEGACION; una consulta
    // en vivo que dijo «no disponible», un escalamiento, un «no me sirvio» y
    // un «escribe la pregunta completa» no. Las respuestas sin resultado
    // (anteriores a V13) no cuentan, ni a favor ni en contra.
    @Test
    void calcular_laTasaDeResolucionSaleDelResultadoGuardado() {
        sinDatos();
        Instant hora = Instant.parse("2026-09-09T15:00:00Z");
        when(mensajeRepository.buscarRespuestasMedidasEntre(eq(Remitente.BOT), any(), any())).thenReturn(List.of(
            new RegistroDeRespuesta(hora, false, 10, ResultadoDeRespuesta.TEMA),
            new RegistroDeRespuesta(hora, false, 10, ResultadoDeRespuesta.CONSULTA_EN_VIVO),
            new RegistroDeRespuesta(hora, false, 10, ResultadoDeRespuesta.NAVEGACION),
            new RegistroDeRespuesta(hora, false, 10, ResultadoDeRespuesta.NO_DISPONIBLE),
            new RegistroDeRespuesta(hora, false, 10, ResultadoDeRespuesta.NO_DISPONIBLE),
            new RegistroDeRespuesta(hora, true, 10, ResultadoDeRespuesta.ESCALADO),
            new RegistroDeRespuesta(hora, true, 10, ResultadoDeRespuesta.NO_SIRVIO),
            new RegistroDeRespuesta(hora, false, 10, ResultadoDeRespuesta.PIDE_REFORMULAR),
            // Anteriores a V13: medidas, pero sin resultado.
            new RegistroDeRespuesta(hora, false, 10),
            new RegistroDeRespuesta(hora, true, 10)));

        AnaliticaChatbot analitica = servicio.calcular(DIA_1, DIA_2, AnaliticaChatbotService.ZONA_POR_DEFECTO);

        assertThat(analitica.respuestasMedidas()).isEqualTo(10);
        assertThat(analitica.respuestasClasificadas()).isEqualTo(8);
        assertThat(analitica.respuestasResueltas()).isEqualTo(3);
        assertThat(analitica.tasaResolucion()).isEqualTo(3.0 / 8);
        // Escalamientos: lo que hizo el asistente (ofrecer soporte), con o sin resultado.
        assertThat(analitica.escalamientos()).isEqualTo(3);
        assertThat(analitica.resultados()).extracting(RespuestasPorResultado::resultado)
            .containsExactly(ResultadoDeRespuesta.values());
        assertThat(analitica.resultados()).extracting(RespuestasPorResultado::respuestas)
            .containsExactly(1L, 1L, 1L, 2L, 1L, 1L, 1L);
    }

    // 1.7.0: «conversaciones iniciadas» por su fecha de inicio, tambien en la
    // tendencia; las que tuvieron preguntas son otra cifra.
    @Test
    void calcular_cuentaLasConversacionesIniciadasPorSuFechaDeInicio() {
        sinDatos();
        UUID vieja = UUID.randomUUID();
        when(conversacionRepository.buscarIniciosEntre(INICIO, FIN)).thenReturn(List.of(
            Instant.parse("2026-09-09T14:00:00Z"),
            Instant.parse("2026-09-10T03:00:00Z"), // 22:00 del 9 en Bogota
            Instant.parse("2026-09-10T20:00:00Z")));
        // Una conversacion que empezo antes del periodo y pregunto dentro de el.
        when(mensajeRepository.buscarPreguntasEntre(eq(Remitente.USUARIO), any(), any())).thenReturn(List.of(
            new RegistroDePregunta(Instant.parse("2026-09-10T15:00:00Z"), vieja)));

        AnaliticaChatbot analitica = servicio.calcular(DIA_1, DIA_2, AnaliticaChatbotService.ZONA_POR_DEFECTO);

        assertThat(analitica.conversaciones()).isEqualTo(3);
        assertThat(analitica.conversacionesConActividad()).isEqualTo(1);
        assertThat(analitica.tendencia()).extracting(PuntoDeTendencia::conversaciones).containsExactly(2L, 1L);
    }

    // 1.3.7: los tickets del periodo y las palabras clave de sus preguntas.
    @Test
    void calcular_incluyeTicketsYPalabrasClave() {
        sinDatos();
        UUID conversacionA = UUID.randomUUID();
        UUID conversacionB = UUID.randomUUID();
        when(ticketSoporteRepository.buscarCreadosEntre(any(), any())).thenReturn(List.of(
            new RegistroDeTicket(EstadoTicket.ABIERTO, Categoria.SOPORTE_TECNICO,
                Instant.parse("2026-09-09T15:00:00Z"), Instant.parse("2026-09-09T15:00:00Z")),
            new RegistroDeTicket(EstadoTicket.RESUELTO, Categoria.SOPORTE_TECNICO,
                Instant.parse("2026-09-09T15:00:00Z"), Instant.parse("2026-09-09T17:00:00Z"))));
        when(mensajeRepository.buscarTextosDePreguntasEntre(eq(Remitente.USUARIO), eq(INICIO), eq(FIN), any()))
            .thenReturn(List.of(
                new RegistroDeTexto(conversacionA, "como funciona el torneo"),
                new RegistroDeTexto(conversacionB, "el torneo empieza cuando")));

        AnaliticaChatbot analitica = servicio.calcular(DIA_1, DIA_2, AnaliticaChatbotService.ZONA_POR_DEFECTO);

        assertThat(analitica.tickets().total()).isEqualTo(2);
        assertThat(analitica.tickets().abiertos()).isEqualTo(1);
        assertThat(analitica.tickets().resueltos()).isEqualTo(1);
        assertThat(analitica.tickets().horasPromedioDeAtencion()).isEqualTo(2.0);
        assertThat(analitica.palabrasClave()).singleElement()
            .satisfies(p -> {
                assertThat(p.palabra()).isEqualTo("torneo");
                assertThat(p.preguntas()).isEqualTo(2);
                assertThat(p.conversaciones()).isEqualTo(2);
                assertThat(p.tendencia()).isEqualTo(TendenciaDePalabra.NUEVA);
            });
    }

    // 1.7.0: la tendencia de una palabra compara con los mismos dias justo
    // antes del periodo, y las preguntas frecuentes salen de las del periodo.
    @Test
    void calcular_comparaLasPalabrasConElPeriodoAnteriorYCuentaLasPreguntasFrecuentes() {
        sinDatos();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        when(mensajeRepository.buscarTextosDePreguntasEntre(eq(Remitente.USUARIO), eq(INICIO), eq(FIN), any()))
            .thenReturn(List.of(
                new RegistroDeTexto(a, "¿Cómo pujo en una subasta?"),
                new RegistroDeTexto(b, "como pujo en una subasta"),
                new RegistroDeTexto(c, "COMO PUJO EN UNA SUBASTA")));
        when(mensajeRepository.buscarTextosDePreguntasEntre(eq(Remitente.USUARIO), eq(INICIO_ANTERIOR), eq(INICIO),
            any()))
            .thenReturn(List.of(new RegistroDeTexto(a, "subasta cancelada")));

        AnaliticaChatbot analitica = servicio.calcular(DIA_1, DIA_2, AnaliticaChatbotService.ZONA_POR_DEFECTO);

        assertThat(analitica.palabrasClave())
            .filteredOn(p -> p.palabra().equals("subasta")).singleElement()
            .satisfies(p -> {
                assertThat(p.preguntas()).isEqualTo(3);
                assertThat(p.preguntasPeriodoAnterior()).isEqualTo(1);
                assertThat(p.tendencia()).isEqualTo(TendenciaDePalabra.SUBE);
            });
        assertThat(analitica.preguntasFrecuentes()).singleElement()
            .satisfies(p -> {
                assertThat(p.texto()).isEqualTo("como pujo en una subasta");
                assertThat(p.preguntas()).isEqualTo(3);
                assertThat(p.conversaciones()).isEqualTo(3);
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
        assertThat(analitica.preguntasFrecuentes()).isEmpty();
        assertThat(analitica.resultados()).allSatisfy(r -> assertThat(r.respuestas()).isZero());
    }

    @Test
    void calcular_hastaAnteriorADesde_lanza400() {
        assertThatThrownBy(() -> servicio.calcular(DIA_2, DIA_1, AnaliticaChatbotService.ZONA_POR_DEFECTO))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void calcular_sinFechas_lanza400() {
        assertThatThrownBy(() -> servicio.calcular(null, DIA_1, AnaliticaChatbotService.ZONA_POR_DEFECTO))
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

    // Indulgente: cada prueba vuelve a responder lo que le interesa.
    private void sinDatos() {
        lenient().when(mensajeRepository.buscarPreguntasEntre(eq(Remitente.USUARIO), any(), any()))
            .thenReturn(List.of());
        lenient().when(mensajeRepository.buscarRespuestasMedidasEntre(eq(Remitente.BOT), any(), any()))
            .thenReturn(List.of());
        lenient().when(calificacionRepository.buscarUtilidadEntre(any(), any())).thenReturn(List.of());
        lenient().when(mensajeRepository.contarRespuestasPorTemaEntre(eq(Remitente.BOT), any(), any(), any()))
            .thenReturn(List.of());
    }
}
