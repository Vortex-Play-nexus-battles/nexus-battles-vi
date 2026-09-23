package com.nexusbattles.ms_chatbot.chat.service;

import com.nexusbattles.ms_chatbot.chat.model.BrechaConocimiento;
import com.nexusbattles.ms_chatbot.chat.repository.BrechaConocimientoRepository;
import com.nexusbattles.ms_chatbot.chat.texto.NormalizadorTexto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

// HU-CHA-011: agrupa las preguntas no cubiertas por su texto normalizado,
// para alimentar despues la base de conocimiento (HU-CHA-004). Dos fuentes:
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

    public BrechaConocimientoService(BrechaConocimientoRepository brechaConocimientoRepository) {
        this.brechaConocimientoRepository = brechaConocimientoRepository;
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
    private Optional<BrechaConocimiento> obtenerOCrear(String pregunta) {
        String normalizado = NormalizadorTexto.normalizar(pregunta);
        if (normalizado.isBlank()) {
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
