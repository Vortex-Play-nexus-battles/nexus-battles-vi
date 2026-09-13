package com.nexusbattles.plataforma.metricasplataforma.disponibilidad;

import java.time.Instant;

import org.springframework.web.client.RestClient;

/**
 * Sonda que pregunta por HTTP al endpoint de salud de Actuator.
 *
 * <p>Regla 3 de plataforma: todo servicio expone salud y metricas de Actuator,
 * y las convenciones de Gradle ya incluyen el starter en los veinte modulos.
 * Por eso no hace falta agente ni instrumentacion nueva en cada servicio: el
 * endpoint ya esta.
 *
 * <p>Cualquier fallo —conexion rechazada, tiempo agotado, 503— cuenta como
 * indisponible con su motivo. Nunca lanza: si la sonda propagara la excepcion,
 * un servicio caido tumbaria la ronda entera y dejaria de medirse el resto.
 */
public class SondaDeActuator implements SondaDeSalud {

    private final RestClient http;

    public SondaDeActuator(RestClient http) {
        this.http = http;
    }

    @Override
    public Comprobacion comprobar(String servicio, String url, Instant instante) {
        try {
            String cuerpo = http.get().uri(url).retrieve().body(String.class);
            boolean sano = cuerpo != null && cuerpo.contains("\"status\":\"UP\"");
            return sano
                    ? Comprobacion.disponible(servicio, instante)
                    : Comprobacion.caido(servicio, instante, "el servicio no reporta UP");
        } catch (RuntimeException e) {
            return Comprobacion.caido(servicio, instante, mensajeDe(e));
        }
    }

    /** El mensaje de la causa raiz dice mas que el de la envoltura de Spring. */
    private static String mensajeDe(RuntimeException e) {
        Throwable causa = e;
        while (causa.getCause() != null) {
            causa = causa.getCause();
        }
        String mensaje = causa.getMessage();
        return mensaje == null || mensaje.isBlank() ? causa.getClass().getSimpleName() : mensaje;
    }
}
