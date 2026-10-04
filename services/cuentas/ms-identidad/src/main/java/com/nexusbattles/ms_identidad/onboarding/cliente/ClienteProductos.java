package com.nexusbattles.ms_identidad.onboarding.cliente;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexusbattles.ms_identidad.onboarding.service.PasoFallido;
import com.nexusbattles.ms_identidad.onboarding.service.PasoFallido.Causa;
import com.nexusbattles.ms_identidad.onboarding.traza.InterceptorDeTraza;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Consulta del catalogo (Grupo 2, {@code GET /api/v1/productos/{id}}, ruta
 * publica): de ahi salen el tipo, el nombre y la parte de armadura de cada
 * producto del kit inicial. El kit se configura solo con identificadores; lo
 * demas lo dice el catalogo, que es su dueno.
 *
 * <p>Sin credencial a proposito: la ruta es publica y un token de mas en una
 * ruta publica puede convertir un fallo de JWKS del catalogo en un 401.
 */
@Component
public class ClienteProductos {

    private final RestClient http;
    private final String base;

    @Autowired
    public ClienteProductos(@Value("${app.onboarding.productos-url:}") String base,
                            InterceptorDeTraza traza) {
        this.base = ClientesHttp.sinBarraFinal(base);
        this.http = ClientesHttp.construir(traza);
    }

    /**
     * @throws PasoFallido {@code CONFIGURACION_INCOMPLETA} si el producto no
     *                     existe: el kit apunta a algo que el catalogo no tiene
     */
    public Producto consultar(String productoId) {
        ClientesHttp.exigirUrl(base, "productos");
        try {
            Producto producto = http.get()
                    .uri(base + "/api/v1/productos/{id}", productoId)
                    .retrieve()
                    .body(Producto.class);
            if (producto == null || producto.tipo() == null) {
                throw new PasoFallido(Causa.RECHAZADO, "productos respondio sin tipo para " + productoId);
            }
            return producto;
        } catch (RestClientResponseException respuesta) {
            if (respuesta.getStatusCode().value() == 404) {
                throw new PasoFallido(Causa.CONFIGURACION_INCOMPLETA,
                        "el producto " + productoId + " del kit inicial no existe en el catalogo", respuesta);
            }
            throw ClientesHttp.traducir("productos", "consultar", respuesta);
        } catch (RuntimeException fallo) {
            throw ClientesHttp.traducir("productos", "consultar", fallo);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Producto(String id, String nombre, String tipo, String parte, String estado) {
    }
}
