package com.nexusbattles.ms_cumplimiento.auditoria.catalogo;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.Objects;

/**
 * {@code GET /api/v1/productos/auditoria/operaciones} de productos.yaml
 * (1.12.0), con la credencial de servicio de esta bitácora — SA-18.
 *
 * <p>Sin reintentos dentro de la ronda: si productos no responde, la ronda
 * siguiente vuelve a pedir desde el mismo punto.
 */
public class ClienteDelCatalogo {

    /** La ruta del contrato; la base ({@code CATALOGO_URL}) no lleva {@code /api/v1}. */
    static final String RUTA = "/api/v1/productos/auditoria/operaciones";

    private final RestClient http;
    private final String base;
    private final CredencialDeServicio credencial;

    public ClienteDelCatalogo(RestClient http, String base, CredencialDeServicio credencial) {
        this.http = Objects.requireNonNull(http, "http");
        this.base = Objects.requireNonNull(base, "base").replaceAll("/+$", "");
        this.credencial = Objects.requireNonNull(credencial, "credencial");
    }

    /**
     * @param desde  punto de lectura exclusivo; nulo, desde la primera operación
     * @param limite cuántas como máximo (1..200)
     * @throws CatalogoNoDisponibleException si no hay credencial, productos no
     *         responde o responde algo que no se entiende
     */
    public LoteDelCatalogo consultar(Instant desde, int limite) {
        String portador = credencial.portador();
        LoteDelCatalogo lote;
        try {
            RestClient.RequestHeadersSpec<?> peticion = desde == null
                ? http.get().uri(base + RUTA + "?limite={limite}", limite)
                : http.get().uri(base + RUTA + "?desde={desde}&limite={limite}", desde.toString(), limite);
            lote = peticion
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + portador)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(LoteDelCatalogo.class);
        } catch (RestClientResponseException rechazo) {
            throw new CatalogoNoDisponibleException("productos respondió " + rechazo.getStatusCode().value(), rechazo);
        } catch (RestClientException noResponde) {
            throw new CatalogoNoDisponibleException(
                "productos no responde: " + noResponde.getClass().getSimpleName(), noResponde);
        }
        if (lote == null || lote.hasta() == null || lote.operaciones() == null) {
            throw new CatalogoNoDisponibleException("productos respondió un lote que no se entiende");
        }
        return lote;
    }
}
