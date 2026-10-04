package com.nexusbattles.ms_identidad.onboarding.service;

/**
 * Un paso del alta no se pudo completar en este intento.
 *
 * <p>La {@link Causa} decide lo que ve el jugador; el mensaje es el detalle
 * tecnico, que va a la bitacora y a {@code onboarding_paso.ultimo_error} y
 * nunca a la pantalla. Todas las causas son reintentables: el alta queda en
 * {@code ERROR_REINTENTABLE} y se vuelve a intentar sola o con el boton
 * «Reintentar».
 */
public class PasoFallido extends RuntimeException {

    public enum Causa {
        /** El servicio dueno no respondio, respondio 5xx o 429. Suele arreglarse solo. */
        SERVICIO_NO_DISPONIBLE,
        /** Falta un valor de configuracion (parametro, URL, producto del kit). */
        CONFIGURACION_INCOMPLETA,
        /** El servicio dueno rechazo la operacion (4xx). Lo tiene que mirar alguien. */
        RECHAZADO,
        /** El paso necesita otro que todavia no esta hecho (el equipo necesita el heroe). */
        DEPENDE_DE_OTRO_PASO
    }

    private final Causa causa;

    public PasoFallido(Causa causa, String detalle) {
        super(detalle);
        this.causa = causa;
    }

    public PasoFallido(Causa causa, String detalle, Throwable origen) {
        super(detalle, origen);
        this.causa = causa;
    }

    public Causa causa() {
        return causa;
    }

    /** Lo que se guarda en {@code ultimo_error}: la causa delante, para poder explicarla despues. */
    public String paraGuardar() {
        return causa.name() + ": " + getMessage();
    }

    /** La causa guardada en {@code ultimo_error}, o nula si el texto no empieza por una. */
    public static Causa causaDe(String guardado) {
        if (guardado == null) {
            return null;
        }
        int corte = guardado.indexOf(':');
        if (corte <= 0) {
            return null;
        }
        try {
            return Causa.valueOf(guardado.substring(0, corte));
        } catch (IllegalArgumentException desconocida) {
            return null;
        }
    }
}
