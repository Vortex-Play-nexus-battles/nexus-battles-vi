package com.nexusbattles.plataforma.notificaciones.catalogo;

import java.time.Instant;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@code GET /api/v1/productos/alertas/cambios} de productos.yaml (1.6.0), con
 * la credencial de servicio de notificaciones (ADR-005) y la traza de la
 * peticion en curso (regla 5): los interceptores los pone
 * {@link ConfiguracionDelCatalogo}, igual que los timeouts.
 *
 * <p>Sin reintentos: la llamada ocurre dentro de una peticion interactiva (la
 * entrega de pendientes de una sesion) y un reintento solo alargaria la
 * espera del jugador. Si no hay lote, la siguiente sesion lo vuelve a pedir.
 */
public class ClienteDeCambiosDelCatalogo implements CambiosDelCatalogo {

    /** La ruta del contrato; la base ({@code PRODUCTOS_BASE_URL}) no lleva {@code /api/v1}. */
    static final String RUTA = "/api/v1/productos/alertas/cambios";

    private final RestClient http;
    private final String base;
    private final int limite;

    public ClienteDeCambiosDelCatalogo(RestClient http, String base, int limite) {
        this.http = http;
        this.base = base.replaceAll("/+$", "");
        this.limite = limite;
    }

    @Override
    public LoteDeCambios consultar(Instant desde) {
        RespuestaDeLote respuesta;
        try {
            RestClient.RequestHeadersSpec<?> peticion = desde == null
                    ? http.get().uri(base + RUTA + "?limite={limite}", limite)
                    : http.get().uri(base + RUTA + "?desde={desde}&limite={limite}", desde.toString(), limite);
            respuesta = peticion
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(RespuestaDeLote.class);
        } catch (RestClientResponseException rechazo) {
            throw new CatalogoNoDisponible("productos respondio " + rechazo.getStatusCode().value());
        } catch (RestClientException noResponde) {
            throw new CatalogoNoDisponible("productos no responde: " + noResponde.getClass().getSimpleName());
        }
        if (respuesta == null || respuesta.hasta() == null || respuesta.alertas() == null) {
            throw new CatalogoNoDisponible("productos respondio un lote que no se entiende");
        }
        // Sin el campo, se da por completo: lo contrario haria consultar en
        // cada sesion, sin intervalo, a un productos que ya no lo envia.
        return new LoteDeCambios(respuesta.hasta(), !Boolean.FALSE.equals(respuesta.completo()), respuesta.alertas());
    }

    /** El esquema LoteDeAlertasCatalogo del contrato, tal como llega. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespuestaDeLote(Instant hasta, Boolean completo, List<CambioDelCatalogo> alertas) {
    }
}
