package com.nexusbattles.ms_identidad.auth.segundofactor;

/**
 * Algo cambio en el segundo factor de una cuenta. Se publica dentro de la
 * transaccion y se audita DESPUES del commit ({@link AuditoriaDelSegundoFactor}):
 * la bitacora no cuenta una activacion que se deshizo.
 *
 * @param afectado  uid de la cuenta (o su clave interna si no tiene uid)
 * @param cambio    que paso
 * @param restantes codigos de recuperacion sin usar tras el cambio (solo al usar uno)
 * @param ip        desde donde (informativa)
 */
public record CambioDeSegundoFactor(String afectado, Cambio cambio, Long restantes, String ip) {

    public enum Cambio {
        ACTIVADO("SEGUNDO_FACTOR_ACTIVADO"),
        DESACTIVADO("SEGUNDO_FACTOR_DESACTIVADO"),
        RECUPERACION_USADA("CODIGO_RECUPERACION_USADO");

        private final String motivo;

        Cambio(String motivo) {
            this.motivo = motivo;
        }

        /** El motivo con el que queda en la auditoria. */
        public String motivo() {
            return motivo;
        }
    }
}
