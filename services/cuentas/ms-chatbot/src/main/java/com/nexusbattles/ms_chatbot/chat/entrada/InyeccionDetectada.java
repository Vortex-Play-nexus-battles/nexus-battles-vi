package com.nexusbattles.ms_chatbot.chat.entrada;

/**
 * 7.4.8 — el texto traía un intento de inyección de código: la petición se
 * rechaza (422, motivo INYECCION_DETECTADA) y no se guarda ni se reenvía nada.
 * El mensaje es para el usuario: no repite lo que escribió ni nombra la regla.
 */
public class InyeccionDetectada extends RuntimeException {

    private final DetectorDeInyeccion.Tipo tipo;

    public InyeccionDetectada(DetectorDeInyeccion.Tipo tipo) {
        super("Tu mensaje parece contener código (etiquetas HTML, instrucciones SQL o comandos) y no se procesó. "
            + "Escribe tu pregunta con palabras.");
        this.tipo = tipo;
    }

    public DetectorDeInyeccion.Tipo tipo() {
        return tipo;
    }
}
