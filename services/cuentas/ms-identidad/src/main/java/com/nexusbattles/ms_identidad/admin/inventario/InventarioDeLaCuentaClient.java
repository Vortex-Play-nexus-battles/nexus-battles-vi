package com.nexusbattles.ms_identidad.admin.inventario;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexusbattles.ms_identidad.onboarding.traza.InterceptorDeTraza;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * La entrega administrativa al inventario de una cuenta (SA-10, §7.3.4 «ver
 * y modificar inventario del usuario»), por {@code POST
 * /api/v1/inventario/entregas} de inventario.yaml 1.8.0.
 *
 * <p><b>Con el token de quien actua.</b> El inventario acepta esa ruta a un
 * ADMINISTRADOR o SUPER_ADMINISTRADOR y anota como quien la pidio al
 * administrador del token, no a ms-identidad: la entrega queda atribuida a la
 * persona que pulso el boton, igual que en la bitacora central.
 *
 * <p><b>Idempotente por clave.</b> La {@code Idempotency-Key} viaja tal cual
 * (con el prefijo de este origen) y la misma clave sirve de
 * {@code referencia}: repetir el intento devuelve la entrega original (200) y
 * nunca duplica elementos. Por eso aqui no se reintenta solo: quien repite es
 * el panel, con la misma clave.
 */
@Component
public class InventarioDeLaCuentaClient {

    static final int CONEXION_MS = 2000;
    static final int LECTURA_MS = 8000;
    /** Distingue las claves de este origen de las de compras, cofres o premios. */
    static final String PREFIJO = "admin-entrega:";
    static final String ORIGEN = "ADMINISTRACION";

    private final RestClient http;
    private final String base;

    @Autowired
    public InventarioDeLaCuentaClient(@Value("${app.admin.inventario-url:}") String base,
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

    /** Un elemento entregado, con lo que identidad usa de {@code ElementoInventario}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ElementoEntregado(String id, String productoId, String tipo, String nombrePropio) {
    }

    /** {@code Entrega} de inventario.yaml, con si la creo esta peticion (201) o ya existia (200). */
    public record Entrega(String id, UUID uid, String origen, String referencia, List<ElementoEntregado> elementos,
                          OffsetDateTime entregadaEn, boolean nueva) {
    }

    /** {@code Entrega} tal como llega del inventario (sin {@code nueva}, que dice el estado HTTP). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record EntregaDelInventario(String id, UUID uid, String origen, String referencia,
                                List<ElementoEntregado> elementos, OffsetDateTime entregadaEn) {
    }

    /**
     * Pide la entrega. 201 o 200 -> la entrega (nueva o la original de esa
     * clave). Un 4xx es el inventario diciendo que no -> {@link
     * EntregaRechazadaException} con su estado y su detalle. 5xx, tiempo
     * agotado o conexion rechazada -> {@link InventarioNoDisponibleException}.
     */
    public Entrega entregar(String autorizacion, String clave, UUID uid, String productoId, int cantidad) {
        if (base.isEmpty()) {
            throw new InventarioNoDisponibleException(
                    "La dirección del inventario no está configurada: no se entregó nada.", null);
        }
        String claveDelOrigen = PREFIJO + clave;
        Map<String, Object> cuerpo = Map.of(
                "uid", uid,
                "origen", ORIGEN,
                "referencia", claveDelOrigen,
                "productos", List.of(Map.of("productoId", productoId, "cantidad", cantidad)));
        ResponseEntity<EntregaDelInventario> respuesta;
        try {
            respuesta = http.post()
                    .uri(base + "/api/v1/inventario/entregas")
                    .header(HttpHeaders.AUTHORIZATION, autorizacion)
                    .header("Idempotency-Key", claveDelOrigen)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cuerpo)
                    .retrieve()
                    .toEntity(EntregaDelInventario.class);
        } catch (RestClientResponseException rechazo) {
            int estado = rechazo.getStatusCode().value();
            if (estado >= 500) {
                throw noDisponible(rechazo);
            }
            throw new EntregaRechazadaException(estado, detalleDe(rechazo));
        } catch (RestClientException sinRespuesta) {
            throw noDisponible(sinRespuesta);
        }
        EntregaDelInventario entrega = respuesta.getBody();
        if (entrega == null || entrega.id() == null) {
            throw new InventarioNoDisponibleException(
                    "El inventario respondió sin la entrega: vuelve a intentarlo con la misma operación.", null);
        }
        return new Entrega(entrega.id(), entrega.uid(), entrega.origen(), entrega.referencia(), entrega.elementos(),
                entrega.entregadaEn(), respuesta.getStatusCode().value() == 201);
    }

    private static InventarioNoDisponibleException noDisponible(Exception causa) {
        return new InventarioNoDisponibleException(
                "El inventario no responde: no se entregó nada. Inténtalo de nuevo en unos segundos.", causa);
    }

    private static String detalleDe(RestClientResponseException rechazo) {
        try {
            ProblemDetail problema = rechazo.getResponseBodyAs(ProblemDetail.class);
            if (problema != null && problema.getDetail() != null && !problema.getDetail().isBlank()) {
                return problema.getDetail();
            }
            if (problema != null && problema.getTitle() != null && !problema.getTitle().isBlank()) {
                return problema.getTitle();
            }
        } catch (RuntimeException cuerpoIlegible) {
            // Sin cuerpo legible: el texto por omision de abajo.
        }
        return "El inventario rechazó la entrega (" + rechazo.getStatusCode().value() + ").";
    }

    private static String sinBarraFinal(String url) {
        String limpia = url == null ? "" : url.trim();
        while (limpia.endsWith("/")) {
            limpia = limpia.substring(0, limpia.length() - 1);
        }
        return limpia;
    }
}
