package com.nexusbattles.ms_chatbot.chat.conocimiento;

import com.nexusbattles.ms_chatbot.chat.conocimiento.ResultadoEvaluacion.FalloDeCaso;
import com.nexusbattles.ms_chatbot.chat.motor.MotorRespuestas;
import com.nexusbattles.ms_chatbot.chat.motor.model.CasoEvaluacion;
import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.motor.model.EstadoVersion;
import com.nexusbattles.ms_chatbot.chat.motor.model.TemaConocimiento;
import com.nexusbattles.ms_chatbot.chat.motor.model.EvaluacionVersion;
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

import java.time.Duration;
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
import static org.mockito.Mockito.lenient;
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
        when(versionRepository.bloquearLaDeEstado(EstadoVersion.BORRADOR)).thenReturn(Optional.of(candidata));
        when(versionRepository.findByEstado(EstadoVersion.PRODUCCION)).thenReturn(Optional.of(produccion));
        when(casoRepository.findByActivoTrue()).thenReturn(List.of());

        assertThatThrownBy(() -> servicio.evaluarCandidata())
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(evaluacionRepository, never()).save(any());
    }

    @Test
    void evaluarCandidata_sinCandidata_lanza409() {
        when(versionRepository.bloquearLaDeEstado(EstadoVersion.BORRADOR)).thenReturn(Optional.empty());
        when(versionRepository.existsByEstado(EstadoVersion.BORRADOR)).thenReturn(false);

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

        // 1.6.0: primero se bloquea produccion y despues la candidata (orden
        // fijo, sin abrazos mortales), y la retirada se escribe ANTES que la
        // nueva produccion (indice parcial de V4).
        InOrder orden = inOrder(versionRepository);
        orden.verify(versionRepository).bloquearLaDeEstado(EstadoVersion.PRODUCCION);
        orden.verify(versionRepository).bloquearLaDeEstado(EstadoVersion.BORRADOR);
        orden.verify(versionRepository).saveAndFlush(produccion);
        orden.verify(versionRepository).saveAndFlush(candidata);
    }

    // 1.6.0: otro despliegue o una reversion cambiaron produccion mientras
    // este esperaba el bloqueo: 409 CAMBIO_CONCURRENTE, sin evaluar ni tocar nada.
    @Test
    void desplegar_siProduccionCambioMientrasEsperaba_lanzaCambioConcurrente() {
        when(versionRepository.bloquearLaDeEstado(EstadoVersion.PRODUCCION)).thenReturn(Optional.empty());
        when(versionRepository.existsByEstado(EstadoVersion.PRODUCCION)).thenReturn(true);

        assertThatThrownBy(() -> servicio.desplegarCandidata()).isInstanceOf(CambioConcurrenteException.class);

        verify(versionRepository, never()).bloquearLaDeEstado(EstadoVersion.BORRADOR);
        verify(evaluacionRepository, never()).save(any());
        verify(versionRepository, never()).saveAndFlush(any());
    }

    // Si la candidata ya no esta (la desplego otro y no hay otra), 409 de siempre.
    @Test
    void desplegar_sinCandidata_lanza409() {
        when(versionRepository.bloquearLaDeEstado(EstadoVersion.PRODUCCION)).thenReturn(Optional.of(produccion));
        when(versionRepository.bloquearLaDeEstado(EstadoVersion.BORRADOR)).thenReturn(Optional.empty());
        when(versionRepository.existsByEstado(EstadoVersion.BORRADOR)).thenReturn(false);

        assertThatThrownBy(() -> servicio.desplegarCandidata())
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(versionRepository, never()).saveAndFlush(any());
    }

    @Test
    void desplegar_sinProduccion_lanza409() {
        when(versionRepository.bloquearLaDeEstado(EstadoVersion.PRODUCCION)).thenReturn(Optional.empty());
        when(versionRepository.existsByEstado(EstadoVersion.PRODUCCION)).thenReturn(false);

        assertThatThrownBy(() -> servicio.desplegarCandidata())
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(e.getReason()).isEqualTo("No hay una version en produccion.");
            });
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

    // ---------------------------------------------- programar (1.3.8)

    @Test
    void programar_enElFuturo_laGuardaEnLaCandidata() {
        when(versionRepository.bloquearLaDeEstado(EstadoVersion.BORRADOR)).thenReturn(Optional.of(candidata));
        when(versionRepository.save(candidata)).thenReturn(candidata);
        Instant cuando = Instant.now().plus(Duration.ofHours(2));

        VersionBaseConocimiento programada = servicio.programarDespliegue(cuando);

        assertThat(programada.getDespliegueProgramadoEn()).isEqualTo(cuando);
    }

    @Test
    void programar_sinFechaMuyProntoOMuyLejos_lanza400SinTocarNada() {
        for (Instant cuando : new Instant[] {null, Instant.now(), Instant.now().plus(Duration.ofDays(91))}) {
            assertThatThrownBy(() -> servicio.programarDespliegue(cuando))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                    e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        verify(versionRepository, never()).save(any());
    }

    @Test
    void cancelar_quitaLaProgramacion() {
        candidata.programarDespliegue(Instant.now().plus(Duration.ofHours(1)));
        when(versionRepository.bloquearLaDeEstado(EstadoVersion.BORRADOR)).thenReturn(Optional.of(candidata));
        when(versionRepository.save(candidata)).thenReturn(candidata);

        assertThat(servicio.cancelarProgramacion().getDespliegueProgramadoEn()).isNull();
    }

    @Test
    void desplegarSiCorresponde_sinProgramacionVencida_noBloqueaNiHaceNada() {
        when(versionRepository.existsByEstadoAndDespliegueProgramadoEnLessThanEqual(EstadoVersion.BORRADOR, ANTES))
            .thenReturn(false);

        assertThat(servicio.desplegarSiCorresponde(ANTES)).isEmpty();

        verify(versionRepository, never()).bloquearLaDeEstado(any());
    }

    // 1.6.0: con varias instancias (o si el administrador la desplego en ese
    // mismo momento), la que espera el bloqueo encuentra la candidata ya
    // publicada y no hace nada.
    @Test
    void desplegarSiCorresponde_siOtraInstanciaYaLaPublico_noHaceNada() {
        programadaYVencida();
        when(versionRepository.bloquearLaDeEstado(EstadoVersion.PRODUCCION)).thenReturn(Optional.of(produccion));
        when(versionRepository.bloquearLaDeEstado(EstadoVersion.BORRADOR)).thenReturn(Optional.empty());
        when(versionRepository.existsByEstado(EstadoVersion.BORRADOR)).thenReturn(false);

        assertThat(servicio.desplegarSiCorresponde(ANTES)).isEmpty();

        verify(versionRepository, never()).saveAndFlush(any());
        verify(evaluacionRepository, never()).save(any());
    }

    // 1.6.0: una reversion cambio produccion mientras se esperaba: esta vuelta
    // no hace nada; la siguiente (un minuto despues) la publica.
    @Test
    void desplegarSiCorresponde_siProduccionCambioMientrasEsperaba_noHaceNada() {
        programadaYVencida();
        when(versionRepository.bloquearLaDeEstado(EstadoVersion.PRODUCCION)).thenReturn(Optional.empty());
        when(versionRepository.existsByEstado(EstadoVersion.PRODUCCION)).thenReturn(true);

        assertThat(servicio.desplegarSiCorresponde(ANTES)).isEmpty();

        assertThat(candidata.getDespliegueProgramadoEn()).isEqualTo(ANTES);
        verify(versionRepository, never()).saveAndFlush(any());
        verify(versionRepository, never()).save(any());
    }

    // El administrador la reprogramo (o cancelo) mientras se esperaba.
    @Test
    void desplegarSiCorresponde_siYaNoEstaVencidaAlBloquearla_noHaceNada() {
        candidata.programarDespliegue(ANTES.plus(Duration.ofHours(1)));
        when(versionRepository.existsByEstadoAndDespliegueProgramadoEnLessThanEqual(EstadoVersion.BORRADOR, ANTES))
            .thenReturn(true);
        when(versionRepository.bloquearLaDeEstado(EstadoVersion.PRODUCCION)).thenReturn(Optional.of(produccion));
        when(versionRepository.bloquearLaDeEstado(EstadoVersion.BORRADOR)).thenReturn(Optional.of(candidata));

        assertThat(servicio.desplegarSiCorresponde(ANTES)).isEmpty();

        assertThat(candidata.getEstado()).isEqualTo(EstadoVersion.BORRADOR);
        verify(versionRepository, never()).saveAndFlush(any());
    }

    @Test
    void desplegarSiCorresponde_vencidaYApta_laDespliega() {
        prepararEvaluacion(copiasEnCandidata(temasDeProduccion));
        programadaYVencida();

        Optional<DespliegueProgramado> resultado = servicio.desplegarSiCorresponde(ANTES);

        assertThat(resultado).hasValueSatisfying(r -> {
            assertThat(r.desplegada()).isTrue();
            assertThat(r.numero()).isEqualTo(2);
        });
        assertThat(candidata.getEstado()).isEqualTo(EstadoVersion.PRODUCCION);
        assertThat(candidata.getDespliegueProgramadoEn()).isNull();
        assertThat(produccion.getEstado()).isEqualTo(EstadoVersion.RETIRADA);
        InOrder orden = inOrder(versionRepository);
        orden.verify(versionRepository).bloquearLaDeEstado(EstadoVersion.PRODUCCION);
        orden.verify(versionRepository).bloquearLaDeEstado(EstadoVersion.BORRADOR);
    }

    // La misma regla que el boton: una candidata que rinde peor no se publica.
    @Test
    void desplegarSiCorresponde_vencidaPeroRindePeor_quedaRechazadaYSinProgramar() {
        prepararEvaluacion(copiasEnCandidata(List.of(temasDeProduccion.get(0))));
        programadaYVencida();

        Optional<DespliegueProgramado> resultado = servicio.desplegarSiCorresponde(ANTES);

        assertThat(resultado).hasValueSatisfying(r -> {
            assertThat(r.desplegada()).isFalse();
            assertThat(r.motivo()).contains("acierta 2 de 3");
        });
        assertThat(candidata.getEstado()).isEqualTo(EstadoVersion.BORRADOR);
        assertThat(candidata.getDespliegueProgramadoEn()).isNull();
        assertThat(candidata.getProgramacionRechazadaEn()).isEqualTo(ANTES);
        verify(versionRepository).save(candidata);
        verify(evaluacionRepository, times(2)).save(any());
    }

    @Test
    void desplegarSiCorresponde_sinCasosActivos_quedaRechazada() {
        programadaYVencida();
        when(versionRepository.bloquearLaDeEstado(EstadoVersion.PRODUCCION)).thenReturn(Optional.of(produccion));
        when(versionRepository.bloquearLaDeEstado(EstadoVersion.BORRADOR)).thenReturn(Optional.of(candidata));
        when(casoRepository.findByActivoTrue()).thenReturn(List.of());

        Optional<DespliegueProgramado> resultado = servicio.desplegarSiCorresponde(ANTES);

        assertThat(resultado).hasValueSatisfying(r -> {
            assertThat(r.desplegada()).isFalse();
            assertThat(r.motivo()).contains("No hay casos de evaluacion activos");
        });
        assertThat(candidata.getProgramacionRechazadaEn()).isEqualTo(ANTES);
    }

    @Test
    void desplegarSiCorresponde_sinProduccion_quedaRechazada() {
        programadaYVencida();
        when(versionRepository.bloquearLaDeEstado(EstadoVersion.PRODUCCION)).thenReturn(Optional.empty());
        when(versionRepository.existsByEstado(EstadoVersion.PRODUCCION)).thenReturn(false);
        when(versionRepository.bloquearLaDeEstado(EstadoVersion.BORRADOR)).thenReturn(Optional.of(candidata));

        Optional<DespliegueProgramado> resultado = servicio.desplegarSiCorresponde(ANTES);

        assertThat(resultado).hasValueSatisfying(r -> {
            assertThat(r.desplegada()).isFalse();
            assertThat(r.motivo()).isEqualTo("No hay una version en produccion.");
        });
        assertThat(candidata.getProgramacionRechazadaEn()).isEqualTo(ANTES);
        verify(versionRepository).save(candidata);
    }

    // ------------------------------------------------------------ revertir

    @Test
    void revertir_restauraLaUltimaRetiradaYRetiraLaActual() {
        VersionBaseConocimiento anterior = versionConId(1);
        anterior.ponerEnProduccion(ANTES);
        anterior.retirar();
        VersionBaseConocimiento actual = versionConId(2);
        actual.ponerEnProduccion(ANTES.plusSeconds(3600));
        when(versionRepository.bloquearLaDeEstado(EstadoVersion.PRODUCCION)).thenReturn(Optional.of(actual));
        when(versionRepository.findFirstByEstadoOrderByFechaDespliegueDesc(EstadoVersion.RETIRADA))
            .thenReturn(Optional.of(anterior));

        VersionBaseConocimiento restaurada = servicio.revertir();

        assertThat(restaurada).isSameAs(anterior);
        assertThat(anterior.getEstado()).isEqualTo(EstadoVersion.PRODUCCION);
        assertThat(anterior.getFechaDespliegue()).isAfter(ANTES.plusSeconds(3600));
        assertThat(actual.getEstado()).isEqualTo(EstadoVersion.RETIRADA);

        InOrder orden = inOrder(versionRepository);
        orden.verify(versionRepository).bloquearLaDeEstado(EstadoVersion.PRODUCCION);
        orden.verify(versionRepository).saveAndFlush(actual);
        orden.verify(versionRepository).saveAndFlush(anterior);
    }

    // 1.6.0: un despliegue cambio produccion mientras la reversion esperaba:
    // no se revierte una version que el administrador no estaba viendo.
    @Test
    void revertir_siProduccionCambioMientrasEsperaba_lanzaCambioConcurrente() {
        when(versionRepository.bloquearLaDeEstado(EstadoVersion.PRODUCCION)).thenReturn(Optional.empty());
        when(versionRepository.existsByEstado(EstadoVersion.PRODUCCION)).thenReturn(true);

        assertThatThrownBy(() -> servicio.revertir()).isInstanceOf(CambioConcurrenteException.class);

        verify(versionRepository, never()).findFirstByEstadoOrderByFechaDespliegueDesc(any());
        verify(versionRepository, never()).saveAndFlush(any());
    }

    @Test
    void revertir_sinVersionAnterior_lanza409() {
        when(versionRepository.bloquearLaDeEstado(EstadoVersion.PRODUCCION)).thenReturn(Optional.of(produccion));
        when(versionRepository.findFirstByEstadoOrderByFechaDespliegueDesc(EstadoVersion.RETIRADA))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.revertir())
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(produccion.getEstado()).isEqualTo(EstadoVersion.PRODUCCION);
        verify(versionRepository, never()).saveAndFlush(any());
    }

    // ------------------------------------------------------------- vigilar

    // Produccion perdio el tema de contrasena respecto a su evaluacion
    // anterior (3 de 3): ahora acierta 2 de 3 -> degradada.
    @Test
    void vigilarProduccion_conTasaMenorQueLaAnterior_laMarcaDegradada() {
        when(versionRepository.findByEstado(EstadoVersion.PRODUCCION)).thenReturn(Optional.of(produccion));
        when(casoRepository.findByActivoTrue()).thenReturn(casosDePrueba());
        when(evaluacionRepository.findFirstByVersionIdOrderByFechaDesc(produccion.getId()))
            .thenReturn(Optional.of(EvaluacionVersion.registrar(produccion, 3, 3, ANTES)));
        when(temaRepository.findByVersionIdOrderByTituloAsc(produccion.getId()))
            .thenReturn(List.of(temasDeProduccion.get(0)));

        Optional<VigilanciaProduccion> vigilancia = servicio.vigilarProduccion();

        assertThat(vigilancia).isPresent();
        assertThat(vigilancia.get().degradada()).isTrue();
        assertThat(vigilancia.get().tasaAnterior()).isEqualTo(1.0);
        assertThat(vigilancia.get().evaluacion().aciertos()).isEqualTo(2);
        verify(evaluacionRepository).save(any());
    }

    @Test
    void vigilarProduccion_sinEvaluacionAnterior_noEsDegradada() {
        when(versionRepository.findByEstado(EstadoVersion.PRODUCCION)).thenReturn(Optional.of(produccion));
        when(casoRepository.findByActivoTrue()).thenReturn(casosDePrueba());
        when(evaluacionRepository.findFirstByVersionIdOrderByFechaDesc(produccion.getId()))
            .thenReturn(Optional.empty());
        when(temaRepository.findByVersionIdOrderByTituloAsc(produccion.getId())).thenReturn(temasDeProduccion);

        Optional<VigilanciaProduccion> vigilancia = servicio.vigilarProduccion();

        assertThat(vigilancia).isPresent();
        assertThat(vigilancia.get().degradada()).isFalse();
        assertThat(vigilancia.get().tasaAnterior()).isNull();
        assertThat(vigilancia.get().evaluacion().aciertos()).isEqualTo(3);
    }

    @Test
    void vigilarProduccion_sinCasosActivos_noEvaluaNada() {
        when(versionRepository.findByEstado(EstadoVersion.PRODUCCION)).thenReturn(Optional.of(produccion));
        when(casoRepository.findByActivoTrue()).thenReturn(List.of());

        assertThat(servicio.vigilarProduccion()).isEmpty();
        verify(evaluacionRepository, never()).save(any());
    }

    // -------------------------------------------------------------- ayudas

    // Casos: dos preguntas que la base sabe responder y una que debe escalar.
    // La candidata se bloquea siempre; produccion se bloquea al desplegar y
    // se lee sin bloquear en la vista previa (lenient: cada prueba usa una).
    private void prepararEvaluacion(List<TemaConocimiento> temasDeCandidata) {
        when(versionRepository.bloquearLaDeEstado(EstadoVersion.BORRADOR)).thenReturn(Optional.of(candidata));
        lenient().when(versionRepository.bloquearLaDeEstado(EstadoVersion.PRODUCCION))
            .thenReturn(Optional.of(produccion));
        lenient().when(versionRepository.findByEstado(EstadoVersion.PRODUCCION)).thenReturn(Optional.of(produccion));
        when(casoRepository.findByActivoTrue()).thenReturn(List.of(
            conId(CasoEvaluacion.nuevo(PREGUNTA_PUJAS, "clave-pujas")),
            conId(CasoEvaluacion.nuevo(PREGUNTA_CONTRASENA, "clave-contrasena")),
            conId(CasoEvaluacion.nuevo(PREGUNTA_FUERA_DE_ALCANCE, null))));
        when(temaRepository.findByVersionIdOrderByTituloAsc(produccion.getId())).thenReturn(temasDeProduccion);
        when(temaRepository.findByVersionIdOrderByTituloAsc(candidata.getId())).thenReturn(temasDeCandidata);
    }

    // La tarea de cada minuto ve una publicacion vencida.
    private void programadaYVencida() {
        candidata.programarDespliegue(ANTES);
        when(versionRepository.existsByEstadoAndDespliegueProgramadoEnLessThanEqual(EstadoVersion.BORRADOR, ANTES))
            .thenReturn(true);
    }

    private static List<CasoEvaluacion> casosDePrueba() {
        return List.of(
            conId(CasoEvaluacion.nuevo(PREGUNTA_PUJAS, "clave-pujas")),
            conId(CasoEvaluacion.nuevo(PREGUNTA_CONTRASENA, "clave-contrasena")),
            conId(CasoEvaluacion.nuevo(PREGUNTA_FUERA_DE_ALCANCE, null)));
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
