package com.nexusbattles.ms_identidad.sanciones;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexusbattles.ms_identidad.auth.servicio.CredencialPropia;
import com.nexusbattles.ms_identidad.auth.validation.ModeracionNoDisponibleException;
import com.nexusbattles.ms_identidad.onboarding.traza.InterceptorDeTraza;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cliente de moderacion-sanciones para las sanciones del panel (B2,
 * moderacion-sanciones-admin.yaml 1.1.0 y -consulta.yaml 1.4.0).
 *
 * <p><b>Con el token de quien actua.</b> Emitir y levantar una sancion viajan
 * con el {@code Authorization} del administrador que pulso el boton: asi
 * moderacion aplica SU regla de roles (el moderador solo suspende de forma
 * temporal; banear y levantar es de administracion) y anota como autor a la
 * persona, no a ms-identidad. La consulta de la sancion activa, que no
 * decide nada, va con la credencial de servicio.
 *
 * <p><b>Sin reintentos.</b> {@code POST /sanciones} no es idempotente: si la
 * respuesta se pierde, reintentar crearia una segunda sancion. Tiempos cortos
 * y, si no hay respuesta, 503: el panel lo dice y la persona decide.
 */
@Component
public class ModeracionSancionesClient {

    static final int CONEXION_MS = 2000;
    static final int LECTURA_MS = 5000;

    private final RestClient http;
    private final String base;
    private final CredencialPropia credencial;

    @Autowired
    public ModeracionSancionesClient(
            @Value("${app.sanciones.url:http://srv-moderacion-sanciones:8086/api/v1}") String base,
            CredencialPropia credencial,
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
        this.credencial = credencial;
    }

    /** La sancion tal como la devuelve moderacion (solo lo que identidad usa). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Sancion(UUID id, String tipo, OffsetDateTime vigenteHasta, Boolean vigente) {
    }

    /** {@code SancionActivaResponse} de moderacion-sanciones-consulta.yaml. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SancionActiva(boolean sancionActiva, String tipo, UUID sancionId, OffsetDateTime vigenteHasta) {
    }

    /** {@code POST /sanciones} con el token de quien actua. */
    public Sancion emitir(String autorizacion, UUID usuarioId, String tipo, String motivo,
                          Long duracionHoras, Boolean confirmacion) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("usuarioId", usuarioId);
        cuerpo.put("tipo", tipo);
        cuerpo.put("motivo", motivo);
        if (duracionHoras != null) {
            cuerpo.put("duracionHoras", duracionHoras);
        }
        if (confirmacion != null) {
            cuerpo.put("confirmacion", confirmacion);
        }
        return llamar(() -> http.post()
                .uri(base + "/sanciones")
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo)
                .retrieve()
                .body(Sancion.class));
    }

    /** {@code GET /sanciones/usuarios/{uid}/activa}, con la credencial de servicio de ms-identidad. */
    public SancionActiva activa(UUID usuarioId) {
        return llamar(() -> http.get()
                .uri(base + "/sanciones/usuarios/{usuarioId}/activa", usuarioId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + credencial.portador())
                .retrieve()
                .body(SancionActiva.class));
    }

    /** {@code POST /sanciones/{sancionId}/levantamiento} con el token de quien actua. */
    public Sancion levantar(String autorizacion, UUID sancionId, String motivo) {
        return llamar(() -> http.post()
                .uri(base + "/sanciones/{sancionId}/levantamiento", sancionId)
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("motivo", motivo))
                .retrieve()
                .body(Sancion.class));
    }

    /**
     * 4xx: moderacion decidio y dijo que no -> {@link SancionRechazadaException}
     * con su detalle. 5xx, tiempo agotado, conexion rechazada o una respuesta
     * vacia: {@link ModeracionNoDisponibleException} (503). Nunca se sigue
     * como si hubiera ido bien.
     */
    private static <T> T llamar(Supplier<T> llamada) {
        T respuesta;
        try {
            respuesta = llamada.get();
        } catch (RestClientResponseException rechazo) {
            int estado = rechazo.getStatusCode().value();
            if (estado >= 500) {
                throw noDisponible(rechazo);
            }
            throw new SancionRechazadaException(estado, detalleDe(rechazo));
        } catch (RestClientException sinRespuesta) {
            throw noDisponible(sinRespuesta);
        }
        if (respuesta == null) {
            throw new ModeracionNoDisponibleException(
                    "moderacion-sanciones respondio sin cuerpo; la sancion no se aplico.");
        }
        return respuesta;
    }

    private static ModeracionNoDisponibleException noDisponible(Exception causa) {
        return new ModeracionNoDisponibleException(
                "El servicio de moderación no responde: la sanción no se aplicó. Inténtalo de nuevo en unos segundos.",
                causa);
    }

    private static String detalleDe(RestClientResponseException rechazo) {
        try {
            ProblemDetail problema = rechazo.getResponseBodyAs(ProblemDetail.class);
            if (problema != null && problema.getDetail() != null && !problema.getDetail().isBlank()) {
                return problema.getDetail();
            }
        } catch (RuntimeException cuerpoIlegible) {
            // Se intenta abajo leyendo el texto.
        }
        Matcher detalle = DETALLE.matcher(rechazo.getResponseBodyAsString(StandardCharsets.UTF_8));
        if (detalle.find() && !detalle.group(1).isBlank()) {
            return detalle.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return "moderacion-sanciones rechazo la operacion (" + rechazo.getStatusCode().value() + ").";
    }

    /**
     * El {@code detail} de un problem details, si el cuerpo no se pudo convertir
     * entero. Cuantificador posesivo: las dos alternativas no se solapan, asi
     * que no hace falta retroceder, y sin retroceso un cuerpo enorme no agota la
     * pila del motor de expresiones.
     */
    private static final Pattern DETALLE = Pattern.compile("\"detail\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*+)\"");

    private static String sinBarraFinal(String url) {
        String limpia = url == null ? "" : url.trim();
        while (limpia.endsWith("/")) {
            limpia = limpia.substring(0, limpia.length() - 1);
        }
        return limpia;
    }
}
