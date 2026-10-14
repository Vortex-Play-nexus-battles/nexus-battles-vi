package com.nexusbattles.ms_chatbot.chat.sugerencias;

import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.motor.model.TemaConocimiento;

// ms-chatbot.yaml 1.3.3: SugerenciaDePregunta. `pregunta` es el titulo del
// tema: MotorRespuestas reconoce un titulo tal cual como pregunta de ese tema,
// asi que pulsar la sugerencia siempre obtiene su respuesta.
// 1.17.0 (7.4.2): en ingles, el titulo en ingles (MotorRespuestas tambien lo
// reconoce, y responde en ingles).
public record SugerenciaResponse(String clave, String titulo, Categoria categoria, String pregunta) {

    public static SugerenciaResponse desde(TemaConocimiento tema) {
        return desde(tema, false);
    }

    public static SugerenciaResponse desde(TemaConocimiento tema, boolean ingles) {
        String titulo = tema.titulo(ingles);
        return new SugerenciaResponse(tema.getClave(), titulo, tema.getCategoria(), titulo);
    }
}
