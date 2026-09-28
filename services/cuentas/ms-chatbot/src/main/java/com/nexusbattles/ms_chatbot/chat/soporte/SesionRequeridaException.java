package com.nexusbattles.ms_chatbot.chat.soporte;

// 401 (motivo SESION_REQUERIDA, ms-chatbot.yaml 1.3.0): los tickets son solo
// para jugadores con sesion; al visitante la ventana le ofrece iniciar sesion
// o el chat general.
public class SesionRequeridaException extends RuntimeException {

    public SesionRequeridaException() {
        super("Para hablar con soporte necesitas iniciar sesion.");
    }
}
