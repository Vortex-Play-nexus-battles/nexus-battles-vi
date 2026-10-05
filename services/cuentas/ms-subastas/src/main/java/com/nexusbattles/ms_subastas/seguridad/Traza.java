package com.nexusbattles.ms_subastas.seguridad;

import com.nexusbattles.comun.observabilidad.FiltroDeTraza;
import org.slf4j.MDC;

import java.net.http.HttpRequest;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Regla 5 de plataforma para los adaptadores de este servicio que usan
 * {@code java.net.http.HttpClient}: la traza viaja en {@code traceparent}.
 *
 * <p>Es lo que {@code InterceptorDeTraza} hace para los {@code RestClient};
 * aqui, igual que {@link PortadorDeServicio} con la credencial, a mano. Los
 * trabajos programados no tienen peticion de la que heredar traza: abren una
 * propia por pasada con {@link #abrir()}.
 */
public final class Traza {

    private static final SecureRandom AZAR = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private Traza() {
    }

    /** Pone {@code traceparent} con la traza en curso, si la hay. */
    public static HttpRequest.Builder propagar(HttpRequest.Builder peticion) {
        String trazaId = MDC.get(FiltroDeTraza.CLAVE_MDC);
        if (trazaId != null && !trazaId.isBlank()) {
            peticion.header(FiltroDeTraza.CABECERA, "00-" + trazaId + "-" + aleatorio(8) + "-01");
        }
        return peticion;
    }

    /**
     * Abre una traza nueva para una pasada de un trabajo programado, si no hay
     * una ya. Se cierra con {@link #cerrar(boolean)} pasando lo que devolvio.
     *
     * @return si la abrio esta llamada
     */
    public static boolean abrir() {
        if (MDC.get(FiltroDeTraza.CLAVE_MDC) != null) {
            return false;
        }
        MDC.put(FiltroDeTraza.CLAVE_MDC, aleatorio(16));
        return true;
    }

    public static void cerrar(boolean abierta) {
        if (abierta) {
            MDC.remove(FiltroDeTraza.CLAVE_MDC);
        }
    }

    private static String aleatorio(int bytes) {
        byte[] crudo = new byte[bytes];
        AZAR.nextBytes(crudo);
        return HEX.formatHex(crudo);
    }
}
