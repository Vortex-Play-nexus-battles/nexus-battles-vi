package com.nexusbattles.ms_identidad.sanciones;

/**
 * moderacion-sanciones respondio y dijo que no (4xx): duracion fuera de
 * rango, rol insuficiente para esa sancion, usuario ya baneado, sancion ya
 * levantada... El panel lo reenvia con el mismo sentido (ver
 * {@code AdminGestionUsuarioController}). No es una caida: una caida es
 * {@code ModeracionNoDisponibleException} (503).
 */
public class SancionRechazadaException extends RuntimeException {

    private final int estado;

    public SancionRechazadaException(int estado, String detalle) {
        super(detalle);
        this.estado = estado;
    }

    /** Estado HTTP con el que respondio moderacion-sanciones. */
    public int getEstado() {
        return estado;
    }
}
