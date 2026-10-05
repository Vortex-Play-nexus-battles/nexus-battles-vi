package com.nexusbattles.plataforma.metricasplataforma.disponibilidad;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import com.nexusbattles.plataforma.metricasplataforma.sondeo.MotivoDeFallo;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Sonda que pregunta por HTTP al endpoint de salud de Actuator.
 *
 * <p>Regla 3 de plataforma: todo servicio expone salud y metricas de Actuator,
 * y las convenciones de Gradle ya incluyen el starter en los veinte modulos.
 * Por eso no hace falta agente ni instrumentacion nueva en cada servicio: el
 * endpoint ya esta.
 *
 * <h2>UP solo con la salud de Actuator (RFINAL-08)</h2>
 *
 * Disponible SOLO si la respuesta es JSON, es un 200 y su estado de arriba
 * —el {@code status} raiz, no el de un componente— dice {@code UP}. Hasta
 * RFINAL-08 bastaba con que un 200 CONTUVIERA el texto {@code "status":"UP"}
 * en cualquier parte: una pagina que llevara ese texto (la del borde, por
 * ejemplo) contaba como salud, igual que un estado raiz {@code UNKNOWN} con
 * algun componente {@code UP}; y un JSON con espacios
 * ({@code "status" : "UP"}) se daba por caido. Ahora una pagina del borde o de
 * Nginx es lo que es: no la salud de Actuator.
 *
 * <p>Cualquier fallo —conexion rechazada, tiempo agotado, 503— cuenta como
 * indisponible con su motivo. Nunca lanza: si la sonda propagara la excepcion,
 * un servicio caido tumbaria la ronda entera y dejaria de medirse el resto.
 * Si conecto pero no contesto a tiempo, la comprobacion lo dice
 * ({@link Comprobacion#esperaAgotada()}): para la cifra de disponibilidad es
 * igual de indisponible, y para la pantalla «Sistema» es LENTO, no CAIDO.
 */
public class SondaDeActuator implements SondaDeSalud {

    private static final ParameterizedTypeReference<Map<String, Object>> CUERPO_JSON =
            new ParameterizedTypeReference<>() { };

    private final RestClient http;
    private final Duration plazoDeConexion;
    private final Duration plazoDeRespuesta;

    /**
     * @param http             cliente con los plazos ya puestos
     * @param plazoDeConexion  el que tiene ese cliente, para decirlo en el motivo
     * @param plazoDeRespuesta el que tiene ese cliente, para decirlo en el motivo
     */
    public SondaDeActuator(RestClient http, Duration plazoDeConexion, Duration plazoDeRespuesta) {
        this.http = http;
        this.plazoDeConexion = plazoDeConexion;
        this.plazoDeRespuesta = plazoDeRespuesta;
    }

    /**
     * Sonda con su propio cliente HTTP y esos dos plazos. Asi el motivo que se
     * muestra («sin respuesta en 1500 ms») es siempre el plazo que de verdad
     * tiene el cliente.
     */
    public static SondaDeActuator conPlazos(Duration conexion, Duration respuesta) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(conexion);
        fabrica.setReadTimeout(respuesta);
        return new SondaDeActuator(RestClient.builder().requestFactory(fabrica).build(), conexion, respuesta);
    }

    @Override
    public Comprobacion comprobar(String servicio, String url, Instant instante) {
        try {
            return http.get().uri(url).accept(MediaType.APPLICATION_JSON)
                    .exchangeForRequiredValue((peticion, respuesta) -> leer(servicio, instante, respuesta));
        } catch (RuntimeException e) {
            String motivo = MotivoDeFallo.describir(e, plazoDeConexion, plazoDeRespuesta);
            return MotivoDeFallo.esperaDeRespuestaAgotada(e)
                    ? Comprobacion.sinRespuesta(servicio, instante, motivo)
                    : Comprobacion.caido(servicio, instante, motivo);
        }
    }

    private static Comprobacion leer(String servicio, Instant instante,
                                     RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse respuesta)
            throws IOException {
        int codigo = respuesta.getStatusCode().value();
        MediaType tipo = respuesta.getHeaders().getContentType();
        if (!esJson(tipo)) {
            return Comprobacion.caido(servicio, instante, "no responde con la salud de Actuator (HTTP " + codigo
                    + (tipo == null ? "" : ", " + tipo.getType() + "/" + tipo.getSubtype()) + ")");
        }
        Map<String, Object> cuerpo;
        try {
            cuerpo = respuesta.bodyTo(CUERPO_JSON);
        } catch (HttpMessageNotReadableException | RestClientException ilegible) {
            return Comprobacion.caido(servicio, instante, "salud de Actuator ilegible (HTTP " + codigo + ")");
        }
        Object estado = cuerpo == null ? null : cuerpo.get("status");
        if (!(estado instanceof String estadoDeActuator)) {
            // Un problem details (404 del borde, por ejemplo) tambien lleva
            // "status", pero numerico: no es la salud de Actuator.
            return Comprobacion.caido(servicio, instante, "respuesta sin el estado de Actuator (HTTP " + codigo + ")");
        }
        if (codigo == 200 && "UP".equals(estadoDeActuator)) {
            return Comprobacion.disponible(servicio, instante);
        }
        return Comprobacion.caido(servicio, instante,
                "el servicio reporta " + estadoDeActuator + " (HTTP " + codigo + ")");
    }

    /** application/json y cualquier +json, como el application/vnd.spring-boot.actuator.v3+json de Actuator. */
    private static boolean esJson(MediaType tipo) {
        return tipo != null
                && "application".equals(tipo.getType())
                && ("json".equals(tipo.getSubtype()) || "json".equals(tipo.getSubtypeSuffix()));
    }
}
