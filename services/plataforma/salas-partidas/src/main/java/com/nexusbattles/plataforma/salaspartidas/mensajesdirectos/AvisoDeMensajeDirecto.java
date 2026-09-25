package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos;

import java.util.UUID;

/**
 * Aviso en la bandeja de notificaciones de quien recibe un mensaje privado sin
 * estar conectado — {@code POST /internal/notifications} de
 * {@code contracts/openapi/notificaciones.yaml}.
 *
 * <p>Mejor esfuerzo: el mensaje ya esta guardado y le espera en el historial.
 * Si el aviso no sale, se pierde el aviso, no el mensaje; por eso este puerto
 * nunca lanza.
 */
public interface AvisoDeMensajeDirecto {

    /**
     * @param mensaje       el que acaba de llegar (quien escribe, cuando)
     * @param primerNoLeido el mensaje que abrio la racha de no leidos de ese
     *                      remitente: da el id del aviso, asi todos los de una
     *                      racha repiten id, notificaciones descarta los
     *                      repetidos (409) y a la bandeja llega uno por racha
     */
    void avisar(MensajeDirecto mensaje, UUID primerNoLeido);
}
