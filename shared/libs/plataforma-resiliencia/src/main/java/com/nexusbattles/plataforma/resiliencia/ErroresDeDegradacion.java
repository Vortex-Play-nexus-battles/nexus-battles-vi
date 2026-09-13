package com.nexusbattles.plataforma.resiliencia;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * El problem detail estandar de una seccion limitada (HU-DIS-003, CA-02).
 *
 * <p>Vive aqui y no en cada servicio por la regla 4 de plataforma: el error
 * tiene que ser identico en los veinte modulos. Y sobre todo por
 * {@code MAPEO-ERRORES.md} §2 —«la interfaz decide por {@code type} y por
 * {@code status}, nunca por el texto»—: si cada servicio inventara su propio
 * {@code type}, el frontend no podria reconocer una seccion degradada sin
 * comparar cadenas, que es exactamente lo que ese documento prohibe.
 *
 * <p>El {@code type} queda registrado en {@code shared/ui-kit/MAPEO-ERRORES.md}
 * §7, que es el sitio que ese documento senala para los tipos con tratamiento
 * propio.
 */
public final class ErroresDeDegradacion {

    /** Identificador estable. Es lo unico sobre lo que el frontend programa. */
    public static final URI TIPO =
            URI.create("https://nexusbattles.local/errores/seccion-no-disponible");

    private ErroresDeDegradacion() {}

    /**
     * Construye la respuesta de una seccion limitada.
     *
     * <p>503 y no 500: 500 significa «este servicio se rompio» y aqui el
     * servicio esta perfectamente, es una dependencia la que no responde. La
     * diferencia importa porque el 5xx se pinta como {@code Error} y el jugador
     * tiene que entender que lo demas si funciona.
     *
     * <p>{@code Retry-After} en segundos, porque la degradacion es temporal por
     * definicion y un cliente automatico necesita saber cuando volver.
     */
    public static ProblemDetail problema(DependenciaDegradada degradada, long reintentarEnSegundos) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "La seccion de " + degradada.seccion() + " no esta disponible temporalmente. "
                        + "El resto del juego sigue funcionando.");

        problema.setType(TIPO);
        problema.setTitle(degradada.seccion() + " no disponible temporalmente");

        // 'seccion' es lo que permite al frontend decir QUE funcion esta
        // limitada, que es lo que pide el criterio. Sin este dato solo podria
        // decir «algo fallo».
        problema.setProperty("seccion", degradada.seccion());
        problema.setProperty("reintentarEnSegundos", reintentarEnSegundos);

        // La dependencia concreta NO se expone al jugador en el texto: es un
        // nombre interno. Va como propiedad aparte, para la bitacora y para el
        // panel de observabilidad.
        problema.setProperty("dependencia", degradada.dependencia());

        return problema;
    }
}
