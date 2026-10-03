package com.nexusbattles.ms_chatbot.chat.conocimiento;

// ms-chatbot.yaml 1.3.8: que paso al llegar la hora de una publicacion
// programada. 'motivo' solo cuando no se desplego.
public record DespliegueProgramado(int numero, boolean desplegada, String motivo) {

    public static DespliegueProgramado desplegada(int numero) {
        return new DespliegueProgramado(numero, true, null);
    }

    public static DespliegueProgramado rechazada(int numero, String motivo) {
        return new DespliegueProgramado(numero, false, motivo);
    }
}
