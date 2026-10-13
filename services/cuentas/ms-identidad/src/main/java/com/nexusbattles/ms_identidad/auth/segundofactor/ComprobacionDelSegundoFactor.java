package com.nexusbattles.ms_identidad.auth.segundofactor;

/**
 * Como se paso el segundo factor en el acceso.
 *
 * @param conRecuperacion si fue con un codigo de recuperacion (y no con la aplicacion)
 * @param restantes       codigos de recuperacion sin usar despues de este; nulo si no se uso ninguno
 */
public record ComprobacionDelSegundoFactor(boolean conRecuperacion, Long restantes) {

    static ComprobacionDelSegundoFactor conAplicacion() {
        return new ComprobacionDelSegundoFactor(false, null);
    }

    static ComprobacionDelSegundoFactor conRecuperacion(long restantes) {
        return new ComprobacionDelSegundoFactor(true, restantes);
    }
}
