package com.nexusbattles.plataforma.correo.envio;

/**
 * Para que sirve el codigo del correo de confirmacion de cuenta (contrato
 * 1.4.0, {@code CorreoConfirmacionCuentaRequest.proposito}).
 *
 * <p>Son dos situaciones distintas con el mismo correo base, y cada una
 * necesita su texto y su enlace: quien se acaba de registrar tiene que probar
 * que el buzon es suyo; a quien le creo la cuenta un Super Administrador le
 * falta elegir su propia contrasena.
 */
public enum PropositoDeConfirmacion {

    /**
     * Cuenta creada por un Super Administrador: el enlace lleva a
     * {@code /restablecer}, donde la persona fija su contrasena. Es el valor
     * por omision porque es lo que ms-identidad enviaba antes de la 1.4.0.
     */
    ACTIVACION("/restablecer"),

    /** Autorregistro de un jugador: el enlace lleva a {@code /verificar}. */
    VERIFICACION("/verificar");

    private final String ruta;

    PropositoDeConfirmacion(String ruta) {
        this.ruta = ruta;
    }

    /** Ruta de la aplicacion a la que lleva el enlace del correo. */
    public String ruta() {
        return ruta;
    }

    /** El proposito guardado en los datos del envio; ACTIVACION si falta o no se reconoce. */
    public static PropositoDeConfirmacion desde(Object valor) {
        return VERIFICACION.name().equals(valor) ? VERIFICACION : ACTIVACION;
    }
}
