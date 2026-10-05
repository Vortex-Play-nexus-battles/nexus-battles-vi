package com.nexusbattles.ms_identidad.privacidad;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexusbattles.ms_identidad.onboarding.traza.InterceptorDeTraza;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Arrays;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Si la persona tiene subastas o pujas abiertas, preguntado a ms-subastas
 * (RF-PRV-005, excepciones: «existencia de subastas activas»).
 *
 * <p><b>Con el token de la persona.</b> Las dos rutas que lo responden
 * ({@code GET /mis-subastas/publicadas} de ms-subastas-panel.yaml 1.0.0 y
 * {@code GET /mis-pujas} de ms-subastas-pujas.yaml 0.4.0) sacan a quien
 * pregunta del {@code uid} del token y no tienen version entre servicios: el
 * {@code Authorization} viaja tal cual, igual que el panel de sanciones manda
 * el del administrador a moderacion-sanciones.
 *
 * <p><b>Fail-closed.</b> Sin respuesta, con un error o con algo que no es una
 * lista, no se sabe: {@link SubastasNoDisponiblesException}, y el cierre no se
 * programa. Nunca se da por bueno «no tiene nada» sin haberlo comprobado.
 * Tiempos cortos: quien espera es la persona que pulso el boton.
 */
@Component
public class ConsultaDeSubastas {

    static final int CONEXION_MS = 2000;
    static final int LECTURA_MS = 5000;

    /** Estados de una participacion que todavia pueden comprometer creditos (0.4.0). */
    static final Set<String> PARTICIPACION_VIGENTE = Set.of("GANANDO", "AUTOMATICA");
    static final String ACTIVA = "ACTIVA";

    private final RestClient http;
    private final String base;

    @Autowired
    public ConsultaDeSubastas(@Value("${identidad.privacidad.subastas-url:http://localhost:8092/api/v1}") String base,
                              InterceptorDeTraza traza) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(CONEXION_MS);
        fabrica.setReadTimeout(LECTURA_MS);
        RestClient.Builder constructor = RestClient.builder().requestFactory(fabrica);
        if (traza != null) {
            constructor.requestInterceptor(traza);
        }
        this.http = constructor.build();
        this.base = sinBarraFinal(base);
    }

    /** Lo abierto que impide cerrar la cuenta. */
    public record OperacionesAbiertas(int subastasActivas, int pujasVigentes) {

        public boolean hay() {
            return subastasActivas > 0 || pujasVigentes > 0;
        }
    }

    /** {@code MiPublicacion}, solo lo que aqui importa. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Publicacion(String estado) {
    }

    /** {@code Participacion}, solo lo que aqui importa. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Participacion(String estadoSubasta, String estado) {
    }

    /**
     * @param autorizacion la cabecera {@code Authorization} de la persona
     * @throws SubastasNoDisponiblesException si no se puede saber
     */
    public OperacionesAbiertas delJugador(String autorizacion) {
        if (base.isEmpty()) {
            throw new SubastasNoDisponiblesException("La URL de ms-subastas no esta configurada en ms-identidad");
        }
        Publicacion[] publicadas = pedir("mis subastas publicadas", () -> http.get()
                .uri(base + "/mis-subastas/publicadas" + "?estado=" + ACTIVA)
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Publicacion[].class));
        Participacion[] pujas = pedir("mis pujas", () -> http.get()
                .uri(base + "/mis-pujas")
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Participacion[].class));

        // Solo las ACTIVA, aunque el filtro ya lo pida: que una publicacion
        // cerrada no impida cerrar la cuenta.
        int activas = (int) Arrays.stream(publicadas)
                .filter(publicacion -> publicacion != null && ACTIVA.equals(publicacion.estado()))
                .count();
        int vigentes = (int) Arrays.stream(pujas)
                .filter(puja -> puja != null && ACTIVA.equals(puja.estadoSubasta())
                        && PARTICIPACION_VIGENTE.contains(puja.estado()))
                .count();
        return new OperacionesAbiertas(activas, vigentes);
    }

    private static <T> T pedir(String que, Supplier<T> llamada) {
        T respuesta;
        try {
            respuesta = llamada.get();
        } catch (RestClientException fallo) {
            // 4xx, 5xx, tiempo agotado, conexion rechazada o un cuerpo que no
            // es la lista esperada: en todos los casos, no se sabe.
            throw new SubastasNoDisponiblesException("ms-subastas no respondio a " + que + ": "
                    + fallo.getClass().getSimpleName(), fallo);
        }
        if (respuesta == null) {
            throw new SubastasNoDisponiblesException("ms-subastas respondio vacio a " + que);
        }
        return respuesta;
    }

    private static String sinBarraFinal(String url) {
        if (url == null) {
            return "";
        }
        String limpia = url.trim();
        while (limpia.endsWith("/")) {
            limpia = limpia.substring(0, limpia.length() - 1);
        }
        return limpia;
    }
}
