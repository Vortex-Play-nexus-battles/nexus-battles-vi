package com.nexusbattles.ms_ecommerce.catalogo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * El catalogo maestro de productos, visto desde la tienda (R16).
 *
 * <p><b>Por que existe.</b> La tienda leia una tabla {@code productos} propia,
 * con ids BIGINT, que nace vacia y no esta conectada a nada: en AWS la vitrina
 * ensenaba cero productos mientras el catalogo tenia los suyos. Los requisitos
 * atan la tienda al catalogo del servicio productos — la vitrina la alimenta
 * la administracion de productos (RF-CAR-001), el tiraje se consume por
 * adquisicion (RF-PRD-003), las modificaciones y suspensiones se tienen que
 * ver en la tienda (RN-PRD-003/004) y el inventario valida el
 * {@code productoId} contra ese catalogo (RF-CAR-010). Asi que la tienda
 * <b>proyecta</b> ese catalogo en lugar de guardar una copia.
 *
 * <p>Las dos consultas son publicas en el servicio productos (sin token):
 * {@code GET /api/v1/productos} (paginado, por omision ACTIVO y UNICO, nunca
 * SUSPENDIDO) y {@code GET /api/v1/productos/{id}}.
 *
 * <p>Todo fallo de transporte —conexion rechazada, tiempo agotado, 5xx,
 * respuesta ilegible— sale como {@link CatalogoNoDisponibleException}; el
 * unico "no" que no es un fallo es que el producto no exista.
 */
@Component
public class CatalogoMaestro {

    private static final Logger log = LoggerFactory.getLogger(CatalogoMaestro.class);

    static final String RUTA_DEL_LISTADO = "/api/v1/productos?page={pagina}&size={tamano}";
    static final String RUTA_DEL_PRODUCTO = "/api/v1/productos/{id}";

    /** El maximo que admite el listado del catalogo (contrato productos 1.2.0). */
    static final int TAMANO_DE_PAGINA = 50;

    /**
     * Tope de paginas por lectura (5000 productos). No es un limite de negocio
     * sino un seguro: un servidor que declarase paginas sin fin no puede dejar
     * a la vitrina leyendo para siempre.
     */
    static final int MAXIMO_DE_PAGINAS = 100;

    /**
     * Forma que puede tener un identificador del catalogo: un segmento de ruta
     * sin caracteres reservados y que cabe en {@code producto_ref}. Los ids
     * que genera el catalogo son UUID, pero no se exige UUID: el banco E2E
     * siembra ids legibles ({@code p-heroe-e2e}) y quien decide si un id
     * existe es el catalogo, no la tienda. Lo que no tiene esta forma no
     * puede existir alli, y no se manda: asi un {@code ../estadisticas} no se
     * convierte en otra ruta del servicio productos.
     */
    private static final Pattern IDENTIFICADOR_POSIBLE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._~-]{0,63}");

    private final RestClient cliente;

    public CatalogoMaestro(@Qualifier(ConfiguracionDelCatalogo.CLIENTE) RestClient cliente) {
        this.cliente = cliente;
    }

    /**
     * Todos los productos del listado por omision del catalogo (ACTIVO y
     * UNICO), en el orden en que el catalogo los publica. Recorre todas las
     * paginas de {@value #TAMANO_DE_PAGINA}.
     *
     * <p>Devuelve lo que publica el catalogo, no lo que se vende: el precio y
     * las existencias los filtra la vitrina.
     *
     * @throws CatalogoNoDisponibleException si alguna pagina no se pudo leer;
     *         nunca se devuelve un catalogo a medias
     */
    public List<ProductoDelCatalogo> productosEnVenta() {
        List<ProductoDelCatalogo> productos = new ArrayList<>();
        for (int pagina = 0; pagina < MAXIMO_DE_PAGINAS; pagina++) {
            PaginaDelCatalogo leida = leerPagina(pagina);
            List<ProductoDelCatalogo> contenido = leida.contenido();
            productos.addAll(contenido);
            if (!leida.hayOtraDespues(pagina, contenido.size())) {
                return List.copyOf(productos);
            }
        }
        log.warn("El catalogo maestro declara mas de {} paginas de {}; la vitrina se queda con los primeros {} productos",
                MAXIMO_DE_PAGINAS, TAMANO_DE_PAGINA, productos.size());
        return List.copyOf(productos);
    }

    /**
     * El producto con ese identificador, este en el estado que este (tambien
     * SUSPENDIDO: decidir si se vende es cosa de quien pregunta).
     *
     * @return vacio si el catalogo no lo tiene (404) o rechaza el
     *         identificador (400), o si el identificador no puede ser de un
     *         producto del catalogo
     * @throws CatalogoNoDisponibleException si el catalogo no se pudo consultar
     */
    public Optional<ProductoDelCatalogo> producto(String id) {
        if (id == null || !IDENTIFICADOR_POSIBLE.matcher(id).matches()) {
            return Optional.empty();
        }
        ProductoDelCatalogo producto;
        try {
            producto = cliente.get()
                    .uri(RUTA_DEL_PRODUCTO, id)
                    .retrieve()
                    .body(ProductoDelCatalogo.class);
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.BadRequest noExiste) {
            return Optional.empty();
        } catch (RestClientException | IllegalArgumentException fallo) {
            throw new CatalogoNoDisponibleException("No se pudo consultar el producto " + id
                    + " en el catalogo maestro: " + fallo.getMessage(), fallo);
        }
        if (producto == null) {
            throw new CatalogoNoDisponibleException("El catalogo maestro respondio sin cuerpo para el producto " + id);
        }
        return Optional.of(producto);
    }

    /*
     * IllegalArgumentException se trata como fallo de transporte porque es lo
     * que lanza el cliente cuando PRODUCTOS_URL viene vacia o no es absoluta:
     * una mala configuracion tiene que verse como "catalogo no disponible"
     * (503 y una linea en la bitacora), no como un 500 sin explicacion.
     */
    private PaginaDelCatalogo leerPagina(int pagina) {
        PaginaDelCatalogo leida;
        try {
            leida = cliente.get()
                    .uri(RUTA_DEL_LISTADO, pagina, TAMANO_DE_PAGINA)
                    .retrieve()
                    .body(PaginaDelCatalogo.class);
        } catch (RestClientException | IllegalArgumentException fallo) {
            throw new CatalogoNoDisponibleException("No se pudo leer la pagina " + pagina
                    + " del catalogo maestro: " + fallo.getMessage(), fallo);
        }
        if (leida == null) {
            throw new CatalogoNoDisponibleException("El catalogo maestro respondio sin cuerpo a la pagina " + pagina);
        }
        return leida;
    }

    /**
     * Una pagina del listado ({@code PaginaDeProductos} del contrato). Solo
     * interesan el contenido y el total de paginas; {@code page},
     * {@code size} y {@code totalElements} se ignoran.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record PaginaDelCatalogo(List<ProductoDelCatalogo> content, Integer totalPages) {

        List<ProductoDelCatalogo> contenido() {
            return content == null ? List.of() : content.stream().filter(Objects::nonNull).toList();
        }

        /**
         * Si hay que pedir la pagina siguiente. Una pagina vacia es el final,
         * diga lo que diga el total (el catalogo pudo encoger entre dos
         * lecturas). Sin total, una pagina llena puede tener siguiente.
         */
        boolean hayOtraDespues(int pagina, int leidos) {
            if (leidos == 0) {
                return false;
            }
            if (totalPages != null) {
                return pagina + 1 < totalPages;
            }
            return leidos >= TAMANO_DE_PAGINA;
        }
    }
}
