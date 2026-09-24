package nexus.aplicacion;

import java.util.Set;

import nexus.api.PaginaDeProductos;
import nexus.dominio.EstadoProducto;
import nexus.dominio.Producto;
import nexus.dominio.TipoProducto;
import nexus.persistencia.ProductoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * Listado paginado del catalogo maestro — R16, contrato productos 1.2.0
 * ({@code GET /api/v1/productos}).
 *
 * <p>Existe porque la vitrina de ms-ecommerce leia una tabla propia que nace
 * vacia y no refleja este catalogo. La decision de arquitectura es que la
 * vitrina proyecte ESTE catalogo, y este listado es lo que consume para
 * hacerlo.
 *
 * <p>Las dos reglas del listado viven aqui y no en el controlador:
 * <ul>
 *   <li>que estados se listan cuando quien llama no pide uno, y</li>
 *   <li>el orden, que tiene que ser estable para que recorrer todas las
 *       paginas no salte ni repita productos.</li>
 * </ul>
 * La validacion de {@code page} y {@code size} es de la frontera HTTP y la
 * hace el controlador con Bean Validation.
 */
@Service
public class ListarProductosServicio {

        /**
         * Estados que se listan cuando no se pide uno explicito: los productos
         * que se pueden mostrar y comprar. SUSPENDIDO nunca entra por omision;
         * solo se obtiene pidiendolo de forma explicita.
         */
        public static final Set<EstadoProducto> ESTADOS_LISTABLES_POR_OMISION =
                Set.of(EstadoProducto.ACTIVO, EstadoProducto.UNICO);

        /**
         * Orden estable entre paginas: fecha de creacion ascendente y, a
         * igualdad, identificador ascendente. Un producto nuevo cae al final,
         * asi que no desplaza las paginas que un consumidor ya leyo.
         */
        public static final Sort ORDEN_DEL_LISTADO = Sort.by(
                Sort.Order.asc("creadoEn"),
                Sort.Order.asc("id"));

        private final ProductoRepository repositorio;
        private final ProductoMapper mapper;

        public ListarProductosServicio(
                        ProductoRepository repositorio,
                        ProductoMapper mapper) {
                this.repositorio = repositorio;
                this.mapper = mapper;
        }

        /**
         * @param pagina numero de pagina, desde 0
         * @param tamano productos por pagina, al menos 1
         * @param tipo si no es nulo, solo ese tipo de producto
         * @param estado si no es nulo, solo ese estado; si es nulo, los
         *               {@link #ESTADOS_LISTABLES_POR_OMISION}
         */
        public PaginaDeProductos listar(
                        int pagina,
                        int tamano,
                        TipoProducto tipo,
                        EstadoProducto estado) {

                Pageable paginacion = PageRequest.of(pagina, tamano, ORDEN_DEL_LISTADO);
                Set<EstadoProducto> estados = estado == null
                        ? ESTADOS_LISTABLES_POR_OMISION
                        : Set.of(estado);

                Page<Producto> resultado = tipo == null
                        ? repositorio.findByEstadoIn(estados, paginacion)
                        : repositorio.findByTipoAndEstadoIn(tipo, estados, paginacion);

                return new PaginaDeProductos(
                        resultado.getContent().stream()
                                .map(mapper::aRespuesta)
                                .toList(),
                        resultado.getNumber(),
                        resultado.getSize(),
                        resultado.getTotalElements(),
                        resultado.getTotalPages());
        }
}
