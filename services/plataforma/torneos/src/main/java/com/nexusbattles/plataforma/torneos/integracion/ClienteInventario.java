package com.nexusbattles.plataforma.torneos.integracion;

import com.nexusbattles.plataforma.torneos.torneo.EntregaDeInventario;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.UUID;

/**
 * inventario.yaml 1.4.0: {@code POST /api/v1/inventario/entregas} con la
 * credencial de servicio de torneos, origen PREMIO_TORNEO e
 * {@code Idempotency-Key}. La misma clave con el mismo cuerpo devuelve la
 * entrega original (200), asi que un reintento nunca duplica la epica.
 *
 * <p>Un 404 aqui es la ruta que inventario todavia no despliega (la
 * implementa B4): se reintenta. Un 409 (la clave ya se uso con otro cuerpo, o
 * el producto esta suspendido) o un 422 (el producto no existe) son
 * definitivos: los arregla quien configura el premio, no otro intento.
 */
public class ClienteInventario implements EntregaDeInventario {

    static final String ORIGEN = "PREMIO_TORNEO";

    private final RestClient http;
    private final String base;

    public ClienteInventario(RestClient http, String base) {
        this.http = http;
        this.base = base.replaceAll("/+$", "");
    }

    @Override
    public void entregarEpica(UUID jugador, String productoId, String referencia, String claveIdempotente) {
        try {
            http.post()
                    .uri(base + "/api/v1/inventario/entregas")
                    .header("Idempotency-Key", claveIdempotente)
                    .body(new SolicitudDeEntrega(jugador.toString(), ORIGEN, referencia,
                            List.of(new Producto(productoId, 1))))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException fallo) {
            throw ClasificadorDeFallos.clasificar("entregar la epica " + productoId, fallo, false);
        }
    }

    record SolicitudDeEntrega(String uid, String origen, String referencia, List<Producto> productos) { }

    record Producto(String productoId, int cantidad) { }
}
