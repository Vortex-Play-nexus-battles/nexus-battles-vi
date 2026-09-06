package com.nexusbattles.plataforma.notificaciones.bandeja;

/** Se lanza cuando el jugador no tiene ningun aviso con ese identificador. */
public class AvisoNoEncontrado extends RuntimeException {

    public AvisoNoEncontrado(String mensaje) {
        super(mensaje);
    }
}
