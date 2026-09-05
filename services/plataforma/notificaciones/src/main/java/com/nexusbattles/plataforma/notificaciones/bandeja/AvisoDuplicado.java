package com.nexusbattles.plataforma.notificaciones.bandeja;

/**
 * Se lanza cuando llega dos veces el mismo evento.
 *
 * <p>El identificador lo pone el modulo que emite, asi que un reintento suyo
 * llega con el mismo valor. Rechazarlo evita que el jugador vea el aviso
 * repetido y que la cuenta de no leidos se infle.
 */
public class AvisoDuplicado extends RuntimeException {

    public AvisoDuplicado(String mensaje) {
        super(mensaje);
    }
}
