package com.nexusbattles.ms_identidad.onboarding.cliente;

import com.nexusbattles.ms_identidad.onboarding.service.PasoFallido;
import com.nexusbattles.ms_identidad.onboarding.service.PasoFallido.Causa;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Lo comun a los cuatro clientes del alta: tiempos de espera y traduccion de
 * errores.
 *
 * <p>Tiempos cortos a proposito (2 s para conectar, 5 s para leer): el alta
 * se reintenta sola, asi que esperar mas a un servicio caido no gana nada y
 * retiene el turno del jugador. Sin tiempos, una llamada a un host que no
 * contesta se queda colgada indefinidamente (ya paso en S8 con salas).
 */
final class ClientesHttp {

    static final int CONEXION_MS = 2000;
    static final int LECTURA_MS = 5000;
    private static final int MAX_CUERPO = 200;

    private ClientesHttp() {
    }

    static RestClient construir(ClientHttpRequestInterceptor... interceptores) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(CONEXION_MS);
        fabrica.setReadTimeout(LECTURA_MS);
        RestClient.Builder constructor = RestClient.builder().requestFactory(fabrica);
        for (ClientHttpRequestInterceptor interceptor : interceptores) {
            if (interceptor != null) {
                constructor.requestInterceptor(interceptor);
            }
        }
        return constructor.build();
    }

    static String sinBarraFinal(String url) {
        if (url == null) {
            return "";
        }
        String limpia = url.trim();
        while (limpia.endsWith("/")) {
            limpia = limpia.substring(0, limpia.length() - 1);
        }
        return limpia;
    }

    static void exigirUrl(String base, String servicio) {
        if (base == null || base.isBlank()) {
            throw new PasoFallido(Causa.CONFIGURACION_INCOMPLETA,
                    "la URL de " + servicio + " no esta configurada en ms-identidad");
        }
    }

    /**
     * Convierte el fallo de una llamada en la causa que entiende el alta.
     * 5xx y 429 son «no disponible» (se arregla solo); el resto de 4xx es un
     * rechazo que alguien tiene que mirar; sin respuesta, «no disponible».
     */
    static PasoFallido traducir(String servicio, String operacion, RuntimeException fallo) {
        if (fallo instanceof PasoFallido yaTraducido) {
            return yaTraducido;
        }
        if (fallo instanceof RestClientResponseException respuesta) {
            int estado = respuesta.getStatusCode().value();
            String texto = servicio + " " + operacion + " respondio " + estado;
            if (estado >= 500 || estado == 429) {
                return new PasoFallido(Causa.SERVICIO_NO_DISPONIBLE, texto, fallo);
            }
            return new PasoFallido(Causa.RECHAZADO,
                    texto + ": " + recortar(respuesta.getResponseBodyAsString()), fallo);
        }
        if (fallo instanceof ResourceAccessException) {
            return new PasoFallido(Causa.SERVICIO_NO_DISPONIBLE,
                    servicio + " " + operacion + " sin respuesta: " + fallo.getMessage(), fallo);
        }
        return new PasoFallido(Causa.SERVICIO_NO_DISPONIBLE,
                servicio + " " + operacion + " fallo: " + fallo.getClass().getSimpleName()
                        + ": " + fallo.getMessage(), fallo);
    }

    static String recortar(String texto) {
        if (texto == null) {
            return "";
        }
        String unaLinea = texto.replaceAll("\\s+", " ").trim();
        return unaLinea.length() <= MAX_CUERPO ? unaLinea : unaLinea.substring(0, MAX_CUERPO) + "...";
    }
}
