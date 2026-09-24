package com.nexusbattles.ms_chatbot.chat.conocimiento;

import com.nexusbattles.ms_chatbot.chat.conocimiento.ResultadoEvaluacion.FalloDeCaso;
import com.nexusbattles.ms_chatbot.chat.motor.MotorRespuestas;
import com.nexusbattles.ms_chatbot.chat.motor.model.CasoEvaluacion;
import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.motor.model.EstadoVersion;
import com.nexusbattles.ms_chatbot.chat.motor.model.TemaConocimiento;
import com.nexusbattles.ms_chatbot.chat.motor.model.TipoRespuesta;
import com.nexusbattles.ms_chatbot.chat.motor.model.VersionBaseConocimiento;
import com.nexusbattles.ms_chatbot.chat.motor.repository.CasoEvaluacionRepository;
import com.nexusbattles.ms_chatbot.chat.motor.repository.EvaluacionVersionRepository;
import com.nexusbattles.ms_chatbot.chat.motor.repository.TemaConocimientoRepository;
import com.nexusbattles.ms_chatbot.chat.motor.repository.VersionBaseConocimientoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Usa el MotorRespuestas REAL: la evaluacion tiene que medir exactamente lo
// que el motor le responderia a un jugador, no una simulacion.
@ExtendWith(MockitoExtension.class)
class EvaluacionBaseConocimientoServiceTest {

    private static final Instant ANTES = Instant.parse("2026-09-01T12:00:00Z");

    private static final String PREGUNTA_PUJAS = "quiero pujar en subastas";
    private static final String PREGUNTA_CONTRASENA = "como cambiar contrasena";
    private static final String PREGUNTA_FUERA_DE_ALCANCE = "como hackeo el juego";

    @Mock
    private VersionBaseConocimientoRepository versionRepository;
    @Mock
    private TemaConocimientoRepository temaRepository;
    @Mock
    private CasoEvaluacionRepository casoRepository;
    @Mock
    private EvaluacionVersionRepository evaluacionRepository;

    private EvaluacionBaseConocimientoService servicio;
    private VersionBaseConocimiento produccion;
    private VersionBaseConocimiento candidata;
    private List<TemaConocimiento> temasDeProduccion;

    @BeforeEach
    void configurar() {
        servicio = new EvaluacionBaseConocimientoService(versionRepository, temaRepository, casoRepository,
            evaluacionRepository, new MotorRespuestas(temaRepository));

        produccion = versionConId(1);
        produccion.ponerEnProduccion(ANTES);
        candidata = versionConId(2);

        temasDeProduccion = List.of(
            tema(produccion, "clave-pujas", "pujar en subastas"),
            tema(produccion, "clave-contrasena", "cambiar contrasena"));
    }

    // ------------------------------------------------------------- evaluar

    @Test
    void evaluarCandidata_igualAProduccion_esAptaYGuardaLasDosEvaluaciones() {
        prepararEvaluacion(copiasEnCandidata(temasDeProduccion));

        ComparacionEvaluacion comparacion = servicio.evaluarCandidata();

        assertThat(comparacion.candidataApta()).isTrue();
        assertThat(comparacion.candidata().aciertos()).isEqualTo(3);
        assertThat(comparacion.produccion().aciertos()).isEqualTo(3);
        assertThat(comparacion.candidata().tasaAcierto()).isEqualTo(1.0);
        assertThat(comparacion.candidata().fallos()).isEmpty();
        verify(evaluacionRepository, times(2)).save(any());
        // Evaluar no despliega nada.
        assertThat(candidata.getEstado()).isEqualTo(EstadoVersion.BORRADOR);
        verify(versionRepository, never()).saveAndFlush(any());
    }

    // Un caso que espera escalar falla si la version lo responde con un tema:
    // la candidata no puede "aprender" a contestar lo que no debe.
    @Test
    void evaluarCandidata_queRespondeLoQueDebiaEscalar_cuentaComoFallo() {
        List<TemaConocimiento> temas = new ArrayList<>(copiasEnCandidata(temasDeProduccion));
        temas.add(tema(candidata, "clave-trucos", "hackeo el juego"));
        prepararEvaluacion(temas);

        ComparacionEvaluacion comparacion = servicio.evaluarCandidata();

        assertThat(comparacion.candidataApta()).isFalse();
        assertThat(comparacion.candidata().fallos())
            .extracting(FalloDeCaso::pregunta, FalloDeCaso::temaClaveEsperada, FalloDeCaso::temaClaveObtenido)
            .containsExactly(org.assertj.core.groups.Tuple.tuple(PREGUNTA_FUERA_DE_ALCANCE, null, "clave-trucos"));
    }

    @Test
    void evaluarCandidata_sinCasosActivos_lanza409() {
        when(versionRepository.findByEstado(EstadoVersion.BORRADOR)).thenReturn(Optional.of(candidata));
        when(versionRepository.findByEstado(EstadoVersion.PRODUCCION)).thenReturn(Optional.of(produccion));
        when(casoRepository.findByActivoTrue()).thenReturn(List.of());

        assertThatThrownBy(() -> servicio.evaluarCandidata())
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(evaluacionRepository, never()).save(any());
    }

    @Test
    void evaluarCandidata_sinCandidata_lanza409() {
        when(versionRepository.findByEstado(EstadoVersion.BORRADOR)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.evaluarCandidata())
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    // ----------------------------------------------------------- desplegar

    @Test
    void desplegar_candidataQueRindeIgual_laPoneEnProduccionYRetiraLaAnterior() {
        prepararEvaluacion(copiasEnCandidata(temasDeProduccion));

        VersionBaseConocimiento desplegada = servicio.desplegarCandidata();

        assertThat(desplegada).isSameAs(candidata);
        assertThat(candidata.getEstado()).isEqualTo(EstadoVersion.PRODUCCION);
        assertThat(candidata.getFechaDespliegue()).isNotNull();
        assertThat(produccion.getEstado()).isEqualTo(EstadoVersion.RETIRADA);

        // La retirada se escribe ANTES que la nueva produccion (indice parcial de V4).
        InOrder orden = inOrder(versionRepository);
        orden.verify(versionRepository).saveAndFlush(produccion);
        orden.verify(versionRepository).saveAndFlush(candidata);
    }

    // Tarea tecnica de HU-CHA-012 (paso 9): una candidata que rinde peor no
    // se despliega. A la candidata le falta el tema de contrasena, asi que
    // acierta 2 de 3 contra 3 de 3 de produccion.
    @Test
    void desplegar_candidataQueRindePeor_noSeDespliega() {
        prepararEvaluacion(copiasEnCandidata(List.of(temasDeProduccion.get(0))));

        DespliegueRechazadoException rechazo = catchThrowableOfType(DespliegueRechazadoException.class,
            () -> servicio.desplegarCandidata());

        assertThat(rechazo).isNotNull();
        assertThat(rechazo.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(rechazo.getComparacion().candidata().aciertos()).isEqualTo(2);
        assertThat(rechazo.getComparacion().produccion().aciertos()).isEqualTo(3);
        assertThat(rechazo.getComparacion().candidata().fallos())
            .extracting(FalloDeCaso::pregunta)
            .containsExactly(PREGUNTA_CONTRASENA);
        assertThat(rechazo.getBody().getProperties()).containsKeys("candidata", "produccion");

        // Nada cambio de estado...
        assertThat(candidata.getEstado()).isEqualTo(EstadoVersion.BORRADOR);
        assertThat(produccion.getEstado()).isEqualTo(EstadoVersion.PRODUCCION);
        verify(versionRepository, never()).saveAndFlush(any());
        // ...pero las dos evaluaciones quedan registradas.
        verify(evaluacionRepository, times(2)).save(any());
    }

    // ------------------------------------------------------------ revertir

    @Test
    void revertir_restauraLaUltimaRetiradaYRetiraLaActual() {
        VersionBaseConocimiento anterior = versionConId(1);
        anterior.ponerEnProduccion(ANTES);
        anterior.retirar();
        VersionBaseConocimiento actual = versionConId(2);
        actual.ponerEnProduccion(ANTES.plusSeconds(3600));
        when(versionRepository.findByEstado(EstadoVersion.PRODUCCION)).thenReturn(Optional.of(actual));
        when(versionRepository.findFirstByEstadoOrderByFechaDespliegueDesc(EstadoVersion.RETIRADA))
            .thenReturn(Optional.of(anterior));

        VersionBaseConocimiento restaurada = servicio.revertir();

        assertThat(restaurada).isSameAs(anterior);
        assertThat(anterior.getEstado()).isEqualTo(EstadoVersion.PRODUCCION);
        assertThat(anterior.getFechaDespliegue()).isAfter(ANTES.plusSeconds(3600));
        assertThat(actual.getEstado()).isEqualTo(EstadoVersion.RETIRADA);

        InOrder orden = inOrder(versionRepository);
        orden.verify(versionRepository).saveAndFlush(actual);
        orden.verify(versionRepository).saveAndFlush(anterior);
    }

    @Test
    void revertir_sinVersionAnterior_lanza409() {
        when(versionRepository.findByEstado(EstadoVersion.PRODUCCION)).thenReturn(Optional.of(produccion));
        when(versionRepository.findFirstByEstadoOrderByFechaDespliegueDesc(EstadoVersion.RETIRADA))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.revertir())
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(produccion.getEstado()).isEqualTo(EstadoVersion.PRODUCCION);
        verify(versionRepository, never()).saveAndFlush(any());
    }

    // -------------------------------------------------------------- ayudas

    // Casos: dos preguntas que la base sabe responder y una que debe escalar.
    private void prepararEvaluacion(List<TemaConocimiento> temasDeCandidata) {
        when(versionRepository.findByEstado(EstadoVersion.BORRADOR)).thenReturn(Optional.of(candidata));
        when(versionRepository.findByEstado(EstadoVersion.PRODUCCION)).thenReturn(Optional.of(produccion));
        when(casoRepository.findByActivoTrue()).thenReturn(List.of(
            conId(CasoEvaluacion.nuevo(PREGUNTA_PUJAS, "clave-pujas")),
            conId(CasoEvaluacion.nuevo(PREGUNTA_CONTRASENA, "clave-contrasena")),
            conId(CasoEvaluacion.nuevo(PREGUNTA_FUERA_DE_ALCANCE, null))));
        when(temaRepository.findByVersionIdOrderByTituloAsc(produccion.getId())).thenReturn(temasDeProduccion);
        when(temaRepository.findByVersionIdOrderByTituloAsc(candidata.getId())).thenReturn(temasDeCandidata);
    }

    private List<TemaConocimiento> copiasEnCandidata(List<TemaConocimiento> temas) {
        return temas.stream().map(t -> t.copiarA(candidata)).toList();
    }

    private static VersionBaseConocimiento versionConId(int numero) {
        return conId(VersionBaseConocimiento.nuevaCandidata(numero, null));
    }

    // El id lo genera JPA al guardar; en una prueba unitaria se asigna a mano.
    private static <T> T conId(T entidad) {
        ReflectionTestUtils.setField(entidad, "id", UUID.randomUUID());
        return entidad;
    }

    private static TemaConocimiento tema(VersionBaseConocimiento version, String clave, String palabrasClaveEs) {
        return new TemaConocimiento(version, clave, Categoria.FAQ_GENERAL, TipoRespuesta.DIRECTA,
            "Tema " + clave, palabrasClaveEs, null, "Respuesta de " + clave, null, 0, true);
    }
}
