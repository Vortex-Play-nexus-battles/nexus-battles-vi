package com.nexusbattles.ms_chatbot.chat.service;

import com.nexusbattles.ms_chatbot.chat.model.BrechaConocimiento;
import com.nexusbattles.ms_chatbot.chat.privacidad.RedaccionDeDatosSensibles;
import com.nexusbattles.ms_chatbot.chat.privacidad.RedactorDeDatosSensibles;
import com.nexusbattles.ms_chatbot.chat.repository.BrechaConocimientoRepository;
import com.nexusbattles.ms_chatbot.chat.texto.NormalizadorTexto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

// HU-CHA-011: agrupa las preguntas no cubiertas por su texto normalizado,
// para alimentar despues la base de conocimiento (HU-CHA-004). La pregunta
// llega ya redactada (ChatService, y la pregunta guardada en el caso de la
// calificacion); se vuelve a redactar aqui igual, porque una brecha sobrevive
// a que el usuario borre su historial y no puede guardar nunca un secreto
// (1.4.0, 7.4.8). Redactar dos veces no cambia nada. Dos fuentes:
//   - registrarNoUtil: el usuario califico "no util" la respuesta.
//   - registrarEscalamiento: MotorRespuestas no entendio la pregunta.
//
// Cada registro corre en su PROPIA transaccion (REQUIRES_NEW). Motivo: esto
// es un efecto secundario de la accion principal (responder un mensaje o
// guardar una calificacion). Si falla (por ejemplo, dos peticiones crean a
// la vez la misma brecha y una choca con el indice unico), solo se pierde
// ese incremento del contador; la transaccion de quien llamo no queda
// marcada para rollback. Quien llama debe atrapar la excepcion para que la
// accion principal siga adelante.
@Service
public class BrechaConocimientoService {

    // Coincide con VARCHAR(500) de brechas_conocimiento.texto_normalizado (V3).
    static final int LONGITUD_MAXIMA_TEXTO_NORMALIZADO = 500;

    private final BrechaConocimientoRepository brechaConocimientoRepository;
    private final RedaccionDeDatosSensibles redaccion;

    public BrechaConocimientoService(BrechaConocimientoRepository brechaConocimientoRepository,
                                     RedaccionDeDatosSensibles redaccion) {
        this.brechaConocimientoRepository = brechaConocimientoRepository;
        this.redaccion = redaccion;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarNoUtil(String pregunta) {
        obtenerOCrear(pregunta).ifPresent(brecha -> {
            brecha.registrarNoUtil();
            brechaConocimientoRepository.save(brecha);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarEscalamiento(String pregunta) {
        obtenerOCrear(pregunta).ifPresent(brecha -> {
            brecha.registrarEscalamiento();
            brechaConocimientoRepository.save(brecha);
        });
    }

    // Vacio si la pregunta no deja texto util al normalizarla (por ejemplo,
    // "???" o solo emojis): no tiene sentido agrupar eso como brecha.
    private Optional<BrechaConocimiento> obtenerOCrear(String preguntaRecibida) {
        String pregunta = redaccion.redactar(preguntaRecibida);
        String normalizado = NormalizadorTexto.normalizar(pregunta);
        // Si quitando lo que se tapo no queda pregunta (solo era un token o
        // una contrasena), no hay nada que agrupar.
        if (normalizado.isBlank() || NormalizadorTexto.normalizar(RedactorDeDatosSensibles.sinMarcas(pregunta)).isBlank()) {
            return Optional.empty();
        }
        if (normalizado.length() > LONGITUD_MAXIMA_TEXTO_NORMALIZADO) {
            normalizado = normalizado.substring(0, LONGITUD_MAXIMA_TEXTO_NORMALIZADO).trim();
        }
        String clave = normalizado;
        return Optional.of(brechaConocimientoRepository.findByTextoNormalizado(clave)
            .orElseGet(() -> new BrechaConocimiento(clave, pregunta.trim())));
    }
}
