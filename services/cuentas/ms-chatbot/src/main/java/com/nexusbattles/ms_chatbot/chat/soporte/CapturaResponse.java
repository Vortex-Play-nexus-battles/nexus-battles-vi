package com.nexusbattles.ms_chatbot.chat.soporte;

import java.time.Instant;
import java.util.UUID;

// ms-chatbot.yaml 1.14.0: CapturaDeSoporte, lo que se ensena de una captura en
// una lista. El contenido se pide aparte, con su propia ruta y su dueno.
public record CapturaResponse(UUID capturaId, String tipo, int tamano, int ancho, int alto, Instant subidaEn) {

    public static CapturaResponse desde(CapturaDeSoporte.Resumen resumen) {
        return new CapturaResponse(resumen.getId(), resumen.getTipo(), resumen.getTamano(), resumen.getAncho(),
            resumen.getAlto(), resumen.getSubidaEn());
    }

    public static CapturaResponse desde(CapturaDeSoporte captura) {
        return new CapturaResponse(captura.getId(), captura.getTipo(), captura.getTamano(), captura.getAncho(),
            captura.getAlto(), captura.getSubidaEn());
    }
}
