package com.nexusbattles.ms_chatbot.chat.soporte;

// 413 (motivo CAPTURA_DEMASIADO_GRANDE, ms-chatbot.yaml 1.14.0): pesa mas de
// 2 MB o mide mas de 4096 pixeles por lado (D-CHA-12).
public class CapturaDemasiadoGrandeException extends RuntimeException {

    public CapturaDemasiadoGrandeException(String motivo) {
        super(motivo);
    }
}
