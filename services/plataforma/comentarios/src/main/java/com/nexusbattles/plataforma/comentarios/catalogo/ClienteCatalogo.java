package com.nexusbattles.plataforma.comentarios.catalogo;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * El catalogo de productos por su API publica — {@code GET
 * {PRODUCTOS_URL}/api/v1/productos/{id}} (productos.yaml, publico), B3.
 *
 * <h2>Que se cree y que no</h2>
 *
 * <ul>
 *   <li><b>404</b>: el producto no existe. Es la unica respuesta que lo
 *       afirma.</li>
 *   <li><b>200 con el mismo {@code id}</b>: existe. Se lee el {@code id} del
 *       cuerpo y se compara con el pedido, y no es desconfianza gratuita: si
 *       {@code PRODUCTOS_URL} apuntara por error a algo que responde 200 a todo
 *       (un borde que sirve una pagina, un eco), sin esta comprobacion
 *       cualquier identificador pasaria por un producto real.</li>
 *   <li><b>Cualquier otra cosa</b> —5xx, otro 4xx, tiempo agotado, conexion
 *       rechazada, un cuerpo que no es un producto—: no se sabe. Las
 *       escrituras responden 503 y las lecturas se degradan.</li>
 * </ul>
 *
 * <p>Los tiempos de conexion y de lectura son cortos (los fija
 * {@code ConfiguracionClientesHttp}): el catalogo vive en el host de
 * contenido, y un comentario no puede quedarse colgado esperando a otro
 * servidor. No lleva la credencial de servicio: la ruta es publica, y un token
 * que el otro host no supiera verificar convertiria un GET abierto en un 401.
 *
 * <p>Los identificadores de mas de 64 caracteres no se preguntan: no caben en
 * la columna {@code producto_id} de este servicio, asi que para el no pueden
 * existir.
 */
@Component
class ClienteCatalogo implements CatalogoDeProductos {

    private static final Logger BITACORA = LoggerFactory.getLogger(ClienteCatalogo.class);

    /** Ruta del contrato productos.yaml; visible para las pruebas y el pacto. */
    static final String RUTA = "/api/v1/productos/{id}";

    /** Largo de la columna producto_id (V1). */
    static final int LARGO_MAXIMO_DEL_ID = 64;

    private final RestClient cliente;
    private final CacheDeExistencia recordadas;

    ClienteCatalogo(
            @Qualifier("restClientProductos") RestClient restClientProductos,
            Clock reloj,
            @Value("${comentarios.productos.cache-positivos:5m}") Duration vidaDeLosPositivos,
            @Value("${comentarios.productos.cache-negativos:30s}") Duration vidaDeLosNegativos) {
        this.cliente = restClientProductos;
        this.recordadas = new CacheDeExistencia(vidaDeLosPositivos, vidaDeLosNegativos, reloj);
    }

    @Override
    public void exigirExistente(String productoId) {
        switch (existencia(productoId)) {
            case EXISTE -> {
                // nada que hacer: se puede escribir sobre el
            }
            case NO_EXISTE -> throw new ProductoInexistente(productoId);
            case DESCONOCIDA -> throw new CatalogoNoDisponible("sin respuesta valida del catalogo");
        }
    }

    @Override
    public Existencia existencia(String productoId) {
        if (productoId == null || productoId.isBlank() || productoId.length() > LARGO_MAXIMO_DEL_ID) {
            return Existencia.NO_EXISTE;
        }
        Optional<Existencia> recordada = recordadas.consultar(productoId);
        if (recordada.isPresent()) {
            return recordada.get();
        }
        Existencia respuesta = preguntar(productoId);
        recordadas.recordar(productoId, respuesta);
        return respuesta;
    }

    private Existencia preguntar(String productoId) {
        try {
            return cliente.get()
                    .uri(RUTA, productoId)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange((peticion, respuesta) -> {
                        int estado = respuesta.getStatusCode().value();
                        if (estado == 404) {
                            return Existencia.NO_EXISTE;
                        }
                        if (!respuesta.getStatusCode().is2xxSuccessful()) {
                            BITACORA.warn("El catalogo respondio {} al consultar el producto {}",
                                    estado, productoId);
                            return Existencia.DESCONOCIDA;
                        }
                        ProductoDelCatalogo producto = respuesta.bodyTo(ProductoDelCatalogo.class);
                        if (producto == null || !productoId.equals(producto.id())) {
                            BITACORA.warn("El catalogo respondio 200 a {} con otro cuerpo: se trata como"
                                    + " sin respuesta", productoId);
                            return Existencia.DESCONOCIDA;
                        }
                        return Existencia.EXISTE;
                    });
        } catch (RuntimeException fallo) {
            // Frontera con otro servicio: tiempo agotado, conexion rechazada o
            // un cuerpo ilegible valen lo mismo, «no se sabe».
            BITACORA.warn("El catalogo no respondio al consultar el producto {}: {}",
                    productoId, fallo.getMessage());
            return Existencia.DESCONOCIDA;
        }
    }

    /** Lo unico que se lee del producto: su identificador. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProductoDelCatalogo(String id) {
    }
}
