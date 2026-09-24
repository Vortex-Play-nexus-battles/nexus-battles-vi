package com.nexusbattles.ms_chatbot.chat.conocimiento;

import com.nexusbattles.ms_chatbot.chat.motor.model.CasoEvaluacion;
import com.nexusbattles.ms_chatbot.chat.motor.repository.CasoEvaluacionRepository;
import com.nexusbattles.ms_chatbot.chat.motor.repository.TemaConocimientoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

// HU-CHA-012 (RF-CHA-014): gestion de los casos con los que se evalua una
// version antes de desplegarla.
@Service
public class CasoEvaluacionService {

    private static final String CASO_NO_ENCONTRADO = "No existe ese caso de evaluacion.";

    private final CasoEvaluacionRepository casoRepository;
    private final TemaConocimientoRepository temaRepository;

    public CasoEvaluacionService(CasoEvaluacionRepository casoRepository,
                                 TemaConocimientoRepository temaRepository) {
        this.casoRepository = casoRepository;
        this.temaRepository = temaRepository;
    }

    @Transactional(readOnly = true)
    public List<CasoEvaluacion> listar() {
        return casoRepository.findAllByOrderByFechaCreacionDesc();
    }

    @Transactional
    public CasoEvaluacion crear(CasoEvaluacionRequest datos) {
        String clave = validarClave(datos.temaClaveEsperada());
        CasoEvaluacion caso = CasoEvaluacion.nuevo(datos.pregunta().trim(), clave);
        if (Boolean.FALSE.equals(datos.activo())) {
            caso.actualizar(caso.getPregunta(), clave, false);
        }
        return casoRepository.save(caso);
    }

    @Transactional
    public CasoEvaluacion editar(UUID casoId, CasoEvaluacionRequest datos) {
        CasoEvaluacion caso = requerirCaso(casoId);
        caso.actualizar(datos.pregunta().trim(), validarClave(datos.temaClaveEsperada()),
            datos.activo() == null || datos.activo());
        return caso;
    }

    @Transactional
    public void eliminar(UUID casoId) {
        casoRepository.delete(requerirCaso(casoId));
    }

    private CasoEvaluacion requerirCaso(UUID casoId) {
        return casoRepository.findById(casoId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, CASO_NO_ENCONTRADO));
    }

    // Vacio = se espera escalar. Si trae clave, tiene que ser la de algun tema
    // (de cualquier version, para poder escribir casos de temas que solo
    // existen en la candidata): un caso con una clave inexistente no se
    // podria acertar nunca y haria fallar toda evaluacion.
    private String validarClave(String clave) {
        if (clave == null || clave.isBlank()) {
            return null;
        }
        String limpia = clave.trim();
        if (temaRepository.findByClaveIn(List.of(limpia)).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "No existe ningun tema con la clave '" + limpia + "'.");
        }
        return limpia;
    }
}
