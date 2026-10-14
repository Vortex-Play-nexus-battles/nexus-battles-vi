package com.nexusbattles.ms_chatbot.chat.soporte;

// 409 (motivo CAPTURAS_COMPLETAS, ms-chatbot.yaml 1.14.0): la solicitud ya
// tiene todas las capturas que admite (D-CHA-12).
public class CapturasCompletasException extends RuntimeException {

    public CapturasCompletasException() {
        super("Esta solicitud ya tiene " + CapturasDeSoporteService.MAXIMO_POR_SOLICITUD + " capturas.");
    }
}
