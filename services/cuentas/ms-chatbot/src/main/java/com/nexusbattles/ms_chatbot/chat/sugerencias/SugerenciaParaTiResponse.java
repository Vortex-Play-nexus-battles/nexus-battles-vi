package com.nexusbattles.ms_chatbot.chat.sugerencias;

import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;

// ms-chatbot.yaml 1.15.0: SugerenciaParaTi. Como SugerenciaDePregunta (pulsarla
// envia `pregunta`, el titulo del tema) mas por que sale; `relacionadoCon` es
// el titulo del tema que la trajo (solo con POR_LO_QUE_PREGUNTASTE).
public record SugerenciaParaTiResponse(String clave, String titulo, Categoria categoria, String pregunta,
                                       MotivoDeSugerencia motivo, String relacionadoCon) {

    public static SugerenciaParaTiResponse desde(SugerenciasParaTiService.SugerenciaParaTi sugerencia) {
        return new SugerenciaParaTiResponse(sugerencia.tema().getClave(), sugerencia.tema().getTitulo(),
            sugerencia.tema().getCategoria(), sugerencia.tema().getTitulo(), sugerencia.motivo(),
            sugerencia.relacionadoCon());
    }
}
