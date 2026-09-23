package com.nexusbattles.plataforma.observabilidad;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.HexFormat;

import com.nexusbattles.comun.observabilidad.FiltroDeTraza;

import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Reenvia el identificador de traza en toda llamada HTTP saliente — regla 5.
 *
 * <h2>La mitad que faltaba</h2>
 *
 * {@link FiltroDeTraza} ya leia el {@code traceparent} de ENTRADA y lo dejaba
 * en el MDC. Lo que no existia era la salida: ningun cliente HTTP del
 * monorepo lo reenviaba. El efecto practico es que la traza moria en el primer
 * salto — salas-partidas llamaba a inventario, a motor-combate y al libro de
 * creditos, y cada uno empezaba una traza nueva. Con veinte servicios eso
 * convierte «seguir una peticion» en «buscar por hora aproximada en veinte
 * bitacoras», que es justamente lo que la regla 5 existe para evitar.
 *
 * <h2>Span nuevo, misma traza</h2>
 *
 * El {@code traceparent} saliente lleva el <b>mismo trace id</b> y un
 * <b>span id nuevo</b>, como manda W3C Trace Context: el tramo padre es esta
 * peticion y el hijo es la llamada. Si no hubiera traza en el MDC —una tarea
 * programada, un arranque, un hilo que no vino de una peticion HTTP— no se
 * inventa ninguna: se deja pasar la peticion sin la cabecera. Poner un trace
 * id nuevo ahi enlazaria dos cosas que no tienen nada que ver.
 *
 * <p>No pisa una cabecera que ya venga puesta: si alguien la fija a mano para
 * un caso concreto, manda la suya.
 */
public final class InterceptorDeTraza implements ClientHttpRequestInterceptor {

    private static final SecureRandom AZAR = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    @Override
    public ClientHttpResponse intercept(
            org.springframework.http.HttpRequest peticion, byte[] cuerpo,
            ClientHttpRequestExecution ejecucion) throws IOException {

        HttpHeaders cabeceras = peticion.getHeaders();
        if (!cabeceras.containsHeader(FiltroDeTraza.CABECERA)) {
            String trazaId = MDC.get(FiltroDeTraza.CLAVE_MDC);
            if (trazaId != null && !trazaId.isBlank()) {
                cabeceras.set(FiltroDeTraza.CABECERA, "00-" + trazaId + "-" + nuevoSpan() + "-01");
            }
        }
        return ejecucion.execute(peticion, cuerpo);
    }

    /** 8 bytes en hexadecimal, que es lo que W3C fija para el span id. */
    private static String nuevoSpan() {
        byte[] bytes = new byte[8];
        AZAR.nextBytes(bytes);
        return HEX.formatHex(bytes);
    }
}
