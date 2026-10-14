package com.nexusbattles.ms_chatbot.chat.conocimiento;

import com.nexusbattles.ms_chatbot.chat.conocimiento.ResultadoEvaluacion.FalloDeCaso;
import com.nexusbattles.ms_chatbot.chat.motor.MotorRespuestas;
import com.nexusbattles.ms_chatbot.chat.motor.ResultadoMotor;
import com.nexusbattles.ms_chatbot.chat.motor.model.CasoEvaluacion;
import com.nexusbattles.ms_chatbot.chat.motor.model.EstadoVersion;
import com.nexusbattles.ms_chatbot.chat.motor.model.EvaluacionVersion;
import com.nexusbattles.ms_chatbot.chat.motor.model.TemaConocimiento;
import com.nexusbattles.ms_chatbot.chat.motor.model.VersionBaseConocimiento;
import com.nexusbattles.ms_chatbot.chat.motor.repository.CasoEvaluacionRepository;
import com.nexusbattles.ms_chatbot.chat.motor.repository.EvaluacionVersionRepository;
import com.nexusbattles.ms_chatbot.chat.motor.repository.TemaConocimientoRepository;
import com.nexusbattles.ms_chatbot.chat.motor.repository.VersionBaseConocimientoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// HU-CHA-012 (RF-CHA-014): el "reentrenamiento" del chatbot.
//
//   evaluar    -> corre los casos activos contra la candidata y contra
//                 produccion, con la MISMA logica del motor, y guarda las dos
//                 evaluaciones.
//   desplegar  -> evalua, y solo si la candidata no acierta menos, la pone en
//                 produccion y retira la vigente.
//   revertir   -> vuelve a poner en produccion la version que estuvo justo
//                 antes de la actual.
//   vigilar    -> evaluacion periodica de produccion para detectar desempeno
//                 degradado (VigilanciaBaseConocimientoTarea).
//   programar  -> (1.3.8) deja la candidata lista para desplegarse sola en
//                 una fecha; al llegar, DespliegueProgramadoTarea hace
//                 exactamente lo mismo que desplegar.
//
// El motor lee la version en produccion en cada mensaje, asi que un
// despliegue o una reversion rigen desde el mensaje siguiente.
//
// ms-chatbot.yaml 1.6.0: desplegar, revertir, programar y la publicacion
// programada se hacen en fila (BloqueoDeVersiones: primero produccion, despues
// la candidata). Nunca quedan dos versiones en produccion ni ninguna; la
// operacion que llega con un estado que otra ya cambio responde 409
// CAMBIO_CONCURRENTE, y la publicacion programada simplemente no hace nada y
// vuelve a mirar en la siguiente vuelta.
@Service
public class EvaluacionBaseConocimientoService {

    private static final String SIN_CANDIDATA = "No hay una version candidata para evaluar.";
    private static final String SIN_PRODUCCION = "No hay una version en produccion.";
    private static final String SIN_CASOS =
        "No hay casos de evaluacion activos. Sin casos, cualquier candidata pasaria la evaluacion.";
    private static final String SIN_ANTERIOR = "No hay una version anterior a la cual revertir.";
    private static final String MUY_PRONTO = "La publicacion se programa al menos un minuto en el futuro.";
    private static final String MUY_LEJOS = "La publicacion se programa como maximo 90 dias en el futuro.";

    // 1.3.8: ni tan pronto que la tarea (cada minuto) ya la hubiera pasado,
    // ni tan lejos que la candidata quede olvidada.
    static final Duration ANTICIPACION_MINIMA = Duration.ofMinutes(1);
    static final Duration ANTICIPACION_MAXIMA = Duration.ofDays(90);

    private final VersionBaseConocimientoRepository versionRepository;
    private final TemaConocimientoRepository temaRepository;
    private final CasoEvaluacionRepository casoRepository;
    private final EvaluacionVersionRepository evaluacionRepository;
    private final MotorRespuestas motorRespuestas;
    private final BloqueoDeVersiones bloqueo;

    public EvaluacionBaseConocimientoService(VersionBaseConocimientoRepository versionRepository,
                                             TemaConocimientoRepository temaRepository,
                                             CasoEvaluacionRepository casoRepository,
                                             EvaluacionVersionRepository evaluacionRepository,
                                             MotorRespuestas motorRespuestas) {
        this.versionRepository = versionRepository;
        this.temaRepository = temaRepository;
        this.casoRepository = casoRepository;
        this.evaluacionRepository = evaluacionRepository;
        this.motorRespuestas = motorRespuestas;
        this.bloqueo = new BloqueoDeVersiones(versionRepository);
    }

    // Vista previa: evalua sin desplegar nada.
    // 1.6.0: con la candidata bloqueada, para que nadie la descarte ni la
    // edite a mitad de la evaluacion (las evaluaciones guardadas apuntan a ella).
    @Transactional
    public ComparacionEvaluacion evaluarCandidata() {
        VersionBaseConocimiento candidata = bloquearCandidata();
        return comparar(candidata, requerirProduccion(), requerirCasos(), Instant.now());
    }

    // noRollbackFor: si la candidata se rechaza, las dos evaluaciones igual
    // quedan guardadas como registro de lo que se intento.
    // 1.6.0: produccion y candidata bloqueadas, en ese orden.
    @Transactional(noRollbackFor = DespliegueRechazadoException.class)
    public VersionBaseConocimiento desplegarCandidata() {
        VersionBaseConocimiento produccion = bloquearProduccion();
        VersionBaseConocimiento candidata = bloquearCandidata();
        return desplegar(candidata, produccion, Instant.now());
    }

    // 1.3.8: programar (o reprogramar) la publicacion de la candidata.
    @Transactional
    public VersionBaseConocimiento programarDespliegue(Instant cuando) {
        Instant ahora = Instant.now();
        if (cuando == null || cuando.isBefore(ahora.plus(ANTICIPACION_MINIMA))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MUY_PRONTO);
        }
        if (cuando.isAfter(ahora.plus(ANTICIPACION_MAXIMA))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MUY_LEJOS);
        }
        VersionBaseConocimiento candidata = bloquearCandidata();
        candidata.programarDespliegue(cuando);
        return versionRepository.save(candidata);
    }

    @Transactional
    public VersionBaseConocimiento cancelarProgramacion() {
        VersionBaseConocimiento candidata = bloquearCandidata();
        candidata.cancelarProgramacion();
        return versionRepository.save(candidata);
    }

    // 1.3.8: lo llama DespliegueProgramadoTarea. Si la candidata tiene una
    // publicacion vencida, la despliega con la misma evaluacion que el boton
    // del panel. Si no pasa la evaluacion, o faltan casos o produccion, queda
    // sin programar y con la marca de rechazo. Vacio si no habia nada que
    // hacer.
    //
    // 1.6.0: con produccion y candidata bloqueadas, como el boton. Con varias
    // instancias, o si un administrador la despliega en ese mismo momento, la
    // que llega despues espera, encuentra la candidata ya publicada (o la
    // produccion cambiada) y no hace nada: la siguiente vuelta vuelve a mirar.
    // Esto reemplaza la reclamacion con un UPDATE suelto de 1.3.8, que ademas
    // se saltaba la `revision`.
    @Transactional(noRollbackFor = DespliegueRechazadoException.class)
    public Optional<DespliegueProgramado> desplegarSiCorresponde(Instant ahora) {
        if (!versionRepository.existsByEstadoAndDespliegueProgramadoEnLessThanEqual(EstadoVersion.BORRADOR, ahora)) {
            return Optional.empty();
        }
        Optional<VersionBaseConocimiento> produccion;
        Optional<VersionBaseConocimiento> candidata;
        try {
            produccion = bloqueo.bloquear(EstadoVersion.PRODUCCION);
            candidata = bloqueo.bloquear(EstadoVersion.BORRADOR);
        } catch (CambioConcurrenteException otraTransicion) {
            return Optional.empty();
        }
        if (candidata.isEmpty() || !candidata.get().despliegueVencido(ahora)) {
            return Optional.empty();
        }
        VersionBaseConocimiento programada = candidata.get();
        int numero = programada.getNumero();
        try {
            VersionBaseConocimiento enProduccion = produccion
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, SIN_PRODUCCION));
            return Optional.of(DespliegueProgramado.desplegada(desplegar(programada, enProduccion, ahora).getNumero()));
        } catch (DespliegueRechazadoException rechazo) {
            marcarProgramacionRechazada(programada, ahora);
            return Optional.of(DespliegueProgramado.rechazada(numero, rechazo.getBody().getDetail()));
        } catch (ResponseStatusException sinCondiciones) {
            marcarProgramacionRechazada(programada, ahora);
            return Optional.of(DespliegueProgramado.rechazada(numero, sinCondiciones.getReason()));
        }
    }

    private void marcarProgramacionRechazada(VersionBaseConocimiento candidata, Instant ahora) {
        candidata.rechazarProgramacion(ahora);
        versionRepository.save(candidata);
    }

    // 1.6.0: con produccion bloqueada. Si otro despliegue u otra reversion la
    // cambio mientras esta esperaba, 409 CAMBIO_CONCURRENTE: no se revierte
    // una version que el administrador no estaba viendo.
    @Transactional
    public VersionBaseConocimiento revertir() {
        VersionBaseConocimiento actual = bloquearProduccion();
        VersionBaseConocimiento anterior = versionRepository
            .findFirstByEstadoOrderByFechaDespliegueDesc(EstadoVersion.RETIRADA)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, SIN_ANTERIOR));

        cambiarProduccion(actual, anterior, Instant.now());
        return anterior;
    }

    // Evalua la version en produccion y la compara con su evaluacion
    // anterior. La version no cambia sola, pero su desempeno si puede bajar:
    // al agregarse casos con preguntas reales, o al cambiar el codigo del
    // motor. Vacio si no hay casos activos (no hay nada que medir).
    @Transactional
    public Optional<VigilanciaProduccion> vigilarProduccion() {
        VersionBaseConocimiento produccion = requerirProduccion();
        List<CasoEvaluacion> casos = casoRepository.findByActivoTrue();
        if (casos.isEmpty()) {
            return Optional.empty();
        }

        // Se lee ANTES de evaluar: despues, la mas reciente seria la nueva.
        Double tasaAnterior = evaluacionRepository.findFirstByVersionIdOrderByFechaDesc(produccion.getId())
            .map(EvaluacionVersion::tasaAcierto)
            .orElse(null);

        return Optional.of(VigilanciaProduccion.de(evaluar(produccion, casos, Instant.now()), tasaAnterior));
    }

    // Un caso se acierta si el motor responde con el tema esperado o, si se
    // esperaba escalar, si escala.
    static boolean acierta(CasoEvaluacion caso, ResultadoMotor resultado) {
        if (caso.esperaEscalamiento()) {
            return resultado.requiereEscalamiento();
        }
        return !resultado.requiereEscalamiento() && caso.getTemaClaveEsperada().equals(resultado.temaClave());
    }

    // Evalua la candidata contra produccion y, solo si no acierta menos, la
    // pone en produccion. Las dos ya vienen bloqueadas.
    private VersionBaseConocimiento desplegar(VersionBaseConocimiento candidata, VersionBaseConocimiento produccion,
                                              Instant ahora) {
        ComparacionEvaluacion comparacion = comparar(candidata, produccion, requerirCasos(), ahora);
        if (!comparacion.candidataApta()) {
            throw new DespliegueRechazadoException(comparacion);
        }
        cambiarProduccion(produccion, candidata, ahora);
        return candidata;
    }

    private ComparacionEvaluacion comparar(VersionBaseConocimiento candidata, VersionBaseConocimiento produccion,
                                           List<CasoEvaluacion> casos, Instant ahora) {
        return ComparacionEvaluacion.de(
            evaluar(candidata, casos, ahora),
            evaluar(produccion, casos, ahora));
    }

    private ResultadoEvaluacion evaluar(VersionBaseConocimiento version, List<CasoEvaluacion> casos, Instant ahora) {
        List<TemaConocimiento> temas = temaRepository.findByVersionIdOrderByTituloAsc(version.getId());

        List<FalloDeCaso> fallos = new ArrayList<>();
        for (CasoEvaluacion caso : casos) {
            ResultadoMotor resultado = motorRespuestas.responderCon(caso.getPregunta(), temas);
            if (!acierta(caso, resultado)) {
                fallos.add(new FalloDeCaso(caso.getId(), caso.getPregunta(),
                    caso.getTemaClaveEsperada(), resultado.temaClave()));
            }
        }

        int aciertos = casos.size() - fallos.size();
        EvaluacionVersion evaluacion = EvaluacionVersion.registrar(version, casos.size(), aciertos, ahora);
        evaluacionRepository.save(evaluacion);
        return new ResultadoEvaluacion(version.getId(), version.getNumero(), casos.size(), aciertos,
            evaluacion.tasaAcierto(), List.copyOf(fallos));
    }

    private void cambiarProduccion(VersionBaseConocimiento saliente, VersionBaseConocimiento entrante, Instant ahora) {
        saliente.retirar();
        // Primero se escribe la retirada: el indice parcial
        // uk_versiones_una_en_produccion de V4 no admite dos versiones en
        // PRODUCCION ni por un instante, y Hibernate no garantiza el orden de
        // los UPDATE si se dejan para el final de la transaccion.
        versionRepository.saveAndFlush(saliente);
        entrante.ponerEnProduccion(ahora);
        versionRepository.saveAndFlush(entrante);
    }

    private VersionBaseConocimiento bloquearCandidata() {
        return bloqueo.bloquear(EstadoVersion.BORRADOR)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, SIN_CANDIDATA));
    }

    private VersionBaseConocimiento bloquearProduccion() {
        return bloqueo.bloquear(EstadoVersion.PRODUCCION)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, SIN_PRODUCCION));
    }

    // Sin bloqueo: para evaluar (la vista previa y la vigilancia) basta leerla.
    private VersionBaseConocimiento requerirProduccion() {
        return versionRepository.findByEstado(EstadoVersion.PRODUCCION)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, SIN_PRODUCCION));
    }

    private List<CasoEvaluacion> requerirCasos() {
        List<CasoEvaluacion> casos = casoRepository.findByActivoTrue();
        if (casos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, SIN_CASOS);
        }
        return casos;
    }
}
