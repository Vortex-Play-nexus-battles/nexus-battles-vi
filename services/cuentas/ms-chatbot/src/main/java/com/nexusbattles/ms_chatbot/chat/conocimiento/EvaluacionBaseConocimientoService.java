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
//
// El motor lee la version en produccion en cada mensaje, asi que un
// despliegue o una reversion rigen desde el mensaje siguiente.
@Service
public class EvaluacionBaseConocimientoService {

    private static final String SIN_CANDIDATA = "No hay una version candidata para evaluar.";
    private static final String SIN_PRODUCCION = "No hay una version en produccion.";
    private static final String SIN_CASOS =
        "No hay casos de evaluacion activos. Sin casos, cualquier candidata pasaria la evaluacion.";
    private static final String SIN_ANTERIOR = "No hay una version anterior a la cual revertir.";

    private final VersionBaseConocimientoRepository versionRepository;
    private final TemaConocimientoRepository temaRepository;
    private final CasoEvaluacionRepository casoRepository;
    private final EvaluacionVersionRepository evaluacionRepository;
    private final MotorRespuestas motorRespuestas;

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
    }

    // Vista previa: evalua sin desplegar nada.
    @Transactional
    public ComparacionEvaluacion evaluarCandidata() {
        return comparar(requerirCandidata(), requerirProduccion(), requerirCasos(), Instant.now());
    }

    // noRollbackFor: si la candidata se rechaza, las dos evaluaciones igual
    // quedan guardadas como registro de lo que se intento.
    @Transactional(noRollbackFor = DespliegueRechazadoException.class)
    public VersionBaseConocimiento desplegarCandidata() {
        VersionBaseConocimiento candidata = requerirCandidata();
        VersionBaseConocimiento produccion = requerirProduccion();
        Instant ahora = Instant.now();

        ComparacionEvaluacion comparacion = comparar(candidata, produccion, requerirCasos(), ahora);
        if (!comparacion.candidataApta()) {
            throw new DespliegueRechazadoException(comparacion);
        }

        cambiarProduccion(produccion, candidata, ahora);
        return candidata;
    }

    @Transactional
    public VersionBaseConocimiento revertir() {
        VersionBaseConocimiento actual = requerirProduccion();
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

    private VersionBaseConocimiento requerirCandidata() {
        return versionRepository.findByEstado(EstadoVersion.BORRADOR)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, SIN_CANDIDATA));
    }

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
