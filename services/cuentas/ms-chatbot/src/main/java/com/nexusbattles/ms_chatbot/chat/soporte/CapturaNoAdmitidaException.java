package com.nexusbattles.ms_chatbot.chat.soporte;

// 415 (motivo CAPTURA_NO_ADMITIDA, ms-chatbot.yaml 1.14.0): no es un PNG ni un
// JPEG bien formado. El detalle dice que paso, nunca repite el contenido.
public class CapturaNoAdmitidaException extends RuntimeException {

    public CapturaNoAdmitidaException(String motivo) {
        super(motivo);
    }
}
