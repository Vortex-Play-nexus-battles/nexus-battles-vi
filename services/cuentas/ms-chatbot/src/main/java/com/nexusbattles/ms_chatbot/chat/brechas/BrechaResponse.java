package com.nexusbattles.ms_chatbot.chat.brechas;

import com.nexusbattles.ms_chatbot.chat.model.BrechaConocimiento;
import com.nexusbattles.ms_chatbot.chat.model.EstadoBrecha;

import java.time.Instant;
import java.util.UUID;

// ms-chatbot.yaml 1.7.0: BrechaDeConocimiento, lo que ve el administrador. Sin
// el texto normalizado (es la clave interna de agrupacion): la pregunta de
// ejemplo, que ya esta redactada (1.4.0).
public record BrechaResponse(
    UUID brechaId,
    String pregunta,
    int escalamientos,
    int noUtiles,
    int total,
    Instant primeraVez,
    Instant ultimaVez,
    EstadoBrecha estado,
    String estadoCambiadoPor,
    Instant estadoCambiadoEn,
    int reaperturas
) {
    public static BrechaResponse desde(BrechaConocimiento brecha) {
        return new BrechaResponse(brecha.getId(), brecha.getEjemploPregunta(), brecha.getContadorEscalamiento(),
            brecha.getContadorNoUtil(), brecha.getTotal(), brecha.getFechaPrimeraOcurrencia(),
            brecha.getFechaUltimaOcurrencia(), brecha.getEstado(), brecha.getEstadoCambiadoPor(),
            brecha.getEstadoCambiadoEn(), brecha.getReaperturas());
    }
}
