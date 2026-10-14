package com.nexusbattles.ms_chatbot.chat.conocimiento;

// 409 (motivo CAMBIO_CONCURRENTE, ms-chatbot.yaml 1.6.0): otra operacion
// cambio las versiones de la base de conocimiento mientras esta esperaba su
// turno (ver BloqueoDeVersiones). No se cambio nada; el panel recarga y el
// administrador decide con el estado actual.
public class CambioConcurrenteException extends RuntimeException {

    static final String MENSAJE = "Otro administrador cambio la base de conocimiento al mismo tiempo "
        + "(otro despliegue, una reversion o un cambio en la candidata). No se cambio nada: revisa el estado "
        + "actual y vuelve a intentarlo.";

    public CambioConcurrenteException() {
        super(MENSAJE);
    }
}
