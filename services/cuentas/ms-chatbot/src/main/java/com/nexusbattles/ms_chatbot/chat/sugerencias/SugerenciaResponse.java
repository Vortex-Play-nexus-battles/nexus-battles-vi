package com.nexusbattles.ms_chatbot.chat.sugerencias;

import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.motor.model.TemaConocimiento;

// ms-chatbot.yaml 1.3.3: SugerenciaDePregunta. `pregunta` es el titulo del
// tema: MotorRespuestas reconoce un titulo tal cual como pregunta de ese tema,
// asi que pulsar la sugerencia siempre obtiene su respuesta.
public record SugerenciaResponse(String clave, String titulo, Categoria categoria, String pregunta) {

    public static SugerenciaResponse desde(TemaConocimiento tema) {
        return new SugerenciaResponse(tema.getClave(), tema.getTitulo(), tema.getCategoria(), tema.getTitulo());
    }
}
