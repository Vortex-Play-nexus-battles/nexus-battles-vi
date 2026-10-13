package com.nexusbattles.plataforma.metricasplataforma.sondeo;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Locale;

/**
 * Por que fallo una llamada de sondeo, en las palabras que lee quien opera el
 * panel (RFINAL-08).
 *
 * <p>Lo importante es separar los dos tiempos agotados, porque significan
 * cosas distintas:
 *
 * <ul>
 *   <li><b>No se pudo conectar</b> dentro del plazo: el host esta apagado (DEV
 *       se apaga de noche) o una regla de red descarta el paquete. Es una
 *       caida.</li>
 *   <li><b>Conecto y no contesto</b> dentro del plazo: el servicio esta vivo
 *       pero lento o colgado. Es LENTO, no CAIDO.</li>
 * </ul>
 *
 * Sirve para los dos clientes HTTP del JDK: {@code HttpURLConnection} (el de
 * {@code SimpleClientHttpRequestFactory}, que es el que se usa) distingue los
 * dos casos por el mensaje de su {@link SocketTimeoutException}, que es el
 * mismo desde hace decadas; el cliente de {@code java.net.http}, por tipo.
 */
public final class MotivoDeFallo {

    private MotivoDeFallo() {
    }

    /** Conecto, pero la respuesta no llego dentro del plazo de lectura. */
    public static boolean esperaDeRespuestaAgotada(Throwable fallo) {
        for (Throwable c = fallo; c != null; c = c.getCause()) {
            if (c instanceof HttpConnectTimeoutException) {
                return false;
            }
            if (c instanceof HttpTimeoutException) {
                return true;
            }
            if (c instanceof SocketTimeoutException tiempo) {
                return !esDeConexion(tiempo);
            }
        }
        return false;
    }

    /** No llego a conectar dentro del plazo. */
    public static boolean conexionAgotada(Throwable fallo) {
        for (Throwable c = fallo; c != null; c = c.getCause()) {
            if (c instanceof HttpConnectTimeoutException) {
                return true;
            }
            if (c instanceof SocketTimeoutException tiempo && esDeConexion(tiempo)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param plazoDeConexion  el configurado, para decirlo; null si no se conoce
     * @param plazoDeRespuesta el configurado, para decirlo; null si no se conoce
     */
    public static String describir(Throwable fallo, Duration plazoDeConexion, Duration plazoDeRespuesta) {
        if (conexionAgotada(fallo)) {
            return "sin conexión " + plazo(plazoDeConexion);
        }
        if (esperaDeRespuestaAgotada(fallo)) {
            return "sin respuesta " + plazo(plazoDeRespuesta);
        }
        Throwable raiz = raiz(fallo);
        String mensaje = raiz.getMessage();
        if (raiz instanceof ConnectException && mensaje != null
                && mensaje.toLowerCase(Locale.ROOT).contains("refused")) {
            return "conexión rechazada";
        }
        if (raiz instanceof UnknownHostException) {
            String host = mensaje == null ? "" : mensaje.split(":", 2)[0].trim();
            return host.isEmpty() ? "no se encuentra el host" : "no se encuentra el host " + host;
        }
        return mensaje == null || mensaje.isBlank() ? raiz.getClass().getSimpleName() : mensaje;
    }

    private static boolean esDeConexion(SocketTimeoutException tiempo) {
        String mensaje = tiempo.getMessage();
        return mensaje != null && mensaje.toLowerCase(Locale.ROOT).contains("connect");
    }

    private static String plazo(Duration plazo) {
        return plazo == null ? "dentro del plazo" : "en " + plazo.toMillis() + " ms";
    }

    /** El mensaje de la causa raiz dice mas que el de la envoltura de Spring. */
    private static Throwable raiz(Throwable fallo) {
        Throwable causa = fallo;
        while (causa.getCause() != null && causa.getCause() != causa) {
            causa = causa.getCause();
        }
        return causa;
    }
}
