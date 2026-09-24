package com.nexusbattles.ms_chatbot.chat.service;

import com.nexusbattles.ms_chatbot.chat.model.Calificacion;
import com.nexusbattles.ms_chatbot.chat.model.Mensaje;
import com.nexusbattles.ms_chatbot.chat.model.Remitente;
import com.nexusbattles.ms_chatbot.chat.repository.CalificacionRepository;
import com.nexusbattles.ms_chatbot.chat.repository.MensajeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

// HU-CHA-011: calificar una respuesta del chatbot como util / no util, con
// comentario opcional.
//
// Reglas:
//   - Solo el dueno de la conversacion puede calificar sus respuestas. Un
//     mensaje de otra sesion responde 404, igual que uno inexistente, para
//     no revelar que existe.
//   - Solo se califican mensajes del BOT (400 si es un mensaje del usuario).
//   - Una sola calificacion por respuesta (409 si ya fue calificada).
//   - Si es "no util", la pregunta del usuario que origino esa respuesta se
//     registra como brecha de conocimiento.
@Service
public class CalificacionService {

    private static final Logger log = LoggerFactory.getLogger(CalificacionService.class);

    private static final String MENSAJE_NO_ENCONTRADO =
        "No existe una respuesta con ese identificador en tu conversacion.";
    private static final String MENSAJE_NO_ES_DEL_BOT =
        "Solo se pueden calificar las respuestas del chatbot.";
    private static final String MENSAJE_YA_CALIFICADO =
        "Esta respuesta ya fue calificada.";

    private final MensajeRepository mensajeRepository;
    private final CalificacionRepository calificacionRepository;
    private final BrechaConocimientoService brechaConocimientoService;

    public CalificacionService(MensajeRepository mensajeRepository, CalificacionRepository calificacionRepository,
                               BrechaConocimientoService brechaConocimientoService) {
        this.mensajeRepository = mensajeRepository;
        this.calificacionRepository = calificacionRepository;
        this.brechaConocimientoService = brechaConocimientoService;
    }

    @Transactional
    public Calificacion calificar(UUID mensajeId, String identificadorSesion, boolean util, String comentario) {
        Mensaje mensaje = mensajeRepository.findByIdAndConversacionIdentificadorSesion(mensajeId, identificadorSesion)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, MENSAJE_NO_ENCONTRADO));

        if (mensaje.getRemitente() != Remitente.BOT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MENSAJE_NO_ES_DEL_BOT);
        }
        if (calificacionRepository.existsByMensajeId(mensajeId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, MENSAJE_YA_CALIFICADO);
        }

        Calificacion calificacion;
        try {
            // saveAndFlush (no save) para que el choque contra el indice unico
            // ocurra AQUI y se pueda traducir a 409. Cubre el caso de dos
            // peticiones simultaneas que pasan ambas el existsByMensajeId.
            calificacion = calificacionRepository.saveAndFlush(
                new Calificacion(mensaje, util, limpiarComentario(comentario)));
        } catch (DataIntegrityViolationException excepcion) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, MENSAJE_YA_CALIFICADO);
        }

        if (!util) {
            registrarBrechaDeLaPreguntaOriginal(mensaje);
        }
        return calificacion;
    }

    // La calificacion ya quedo guardada; si registrar la brecha falla, se
    // deja constancia en el log y la calificacion sigue en pie (ver el
    // comentario de BrechaConocimientoService sobre REQUIRES_NEW).
    private void registrarBrechaDeLaPreguntaOriginal(Mensaje respuestaDelBot) {
        mensajeRepository
            .findFirstByConversacionIdAndRemitenteAndFechaEnvioLessThanEqualOrderByFechaEnvioDesc(
                respuestaDelBot.getConversacion().getId(), Remitente.USUARIO, respuestaDelBot.getFechaEnvio())
            .ifPresent(pregunta -> {
                try {
                    brechaConocimientoService.registrarNoUtil(pregunta.getContenido());
                } catch (RuntimeException excepcion) {
                    log.warn("No se pudo registrar la brecha de conocimiento de la respuesta {}",
                        respuestaDelBot.getId(), excepcion);
                }
            });
    }

    // Un comentario vacio o solo con espacios se guarda como "sin comentario".
    private String limpiarComentario(String comentario) {
        if (comentario == null || comentario.isBlank()) {
            return null;
        }
        return comentario.trim();
    }
}
