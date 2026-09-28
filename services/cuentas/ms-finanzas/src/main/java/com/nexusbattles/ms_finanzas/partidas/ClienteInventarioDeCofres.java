package com.nexusbattles.ms_finanzas.partidas;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Adaptador de {@link InventarioDeCofres} contra inventario.yaml 1.4.0 —
 * {@code POST /api/v1/inventario/entregas}, origen {@code COFRE},
 * {@code Idempotency-Key: cofre-{id}}.
 *
 * <p>El {@link RestClient} llega con la credencial de servicio de ms-finanzas
 * (ADR-005, {@code DIRECTORIO_ACTIVO_*}): inventario solo acepta entregas de un
 * servicio o de un administrador. Con tiempos de espera acotados: la entrega
 * corre fuera de la transacción, pero en el hilo de la petición.
 *
 * <p>Cualquier respuesta que no sea la entrega hecha es
 * {@link InventarioDeCofres.EntregaNoRealizada}: un 404 es la ruta que
 * inventario todavía no despliega (la implementa B4), un 5xx o una conexión
 * rechazada son una caída, y un 409 o un 422 (producto suspendido o que no
 * existe) los arregla quien configura la tabla del cofre. En todos los casos
 * el cofre queda {@code PENDIENTE} con el motivo y se reintenta: el premio del
 * jugador no se pierde.
 */
public class ClienteInventarioDeCofres implements InventarioDeCofres {

    static final String ORIGEN = "COFRE";

    private final RestClient http;
    private final String base;

    public ClienteInventarioDeCofres(RestClient http, String base) {
        this.http = http;
        this.base = base == null ? "" : base.replaceAll("/+$", "");
    }

    @Override
    public String entregar(CofreEntregado cofre) {
        if (base.isBlank()) {
            throw new EntregaNoRealizada("INVENTARIO_BASE_URL no está configurada: el cofre queda pendiente");
        }
        List<Producto> productos = cofre.getPremios().stream()
                .map(p -> new Producto(p.getProductoId(), p.getCantidad()))
                .toList();
        Entrega entrega;
        try {
            entrega = http.post()
                    .uri(base + "/api/v1/inventario/entregas")
                    .header("Idempotency-Key", cofre.claveDeEntrega())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SolicitudDeEntrega(cofre.getUidJugador(), ORIGEN, cofre.claveDeEntrega(), productos))
                    .retrieve()
                    .body(Entrega.class);
        } catch (RestClientResponseException rechazo) {
            throw new EntregaNoRealizada("inventario respondio " + rechazo.getStatusCode().value()
                    + " a la entrega del " + cofre.claveDeEntrega(), rechazo);
        } catch (RuntimeException sinRespuesta) {
            // Conexion rechazada, tiempo agotado, cuerpo ilegible o credencial
            // de servicio que no se pudo obtener (el interceptor lanza la suya).
            throw new EntregaNoRealizada("inventario no respondio a la entrega del " + cofre.claveDeEntrega()
                    + ": " + sinRespuesta.getMessage(), sinRespuesta);
        }
        if (entrega == null || entrega.id() == null || entrega.id().isBlank()) {
            throw new EntregaNoRealizada("inventario respondio a la entrega del " + cofre.claveDeEntrega()
                    + " sin identificador de entrega");
        }
        return entrega.id();
    }

    /** Esquema {@code SolicitudDeEntrega} de inventario.yaml 1.4.0. */
    record SolicitudDeEntrega(String uid, String origen, String referencia, List<Producto> productos) { }

    record Producto(String productoId, int cantidad) { }

    /** De la {@code Entrega} solo hace falta su identificador. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Entrega(String id) { }
}
