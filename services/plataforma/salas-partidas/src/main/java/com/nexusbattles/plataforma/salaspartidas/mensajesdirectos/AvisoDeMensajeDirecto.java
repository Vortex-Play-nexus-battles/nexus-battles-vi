package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos;

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

    void avisar(MensajeDirecto mensaje);
}
