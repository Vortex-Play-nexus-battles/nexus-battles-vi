package nexus.api;

import java.net.URI;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import nexus.aplicacion.ConsultarProductoServicio;
import nexus.aplicacion.CrearProductoServicio;
import nexus.aplicacion.ConsultarEstadoCatalogoServicio;
import nexus.aplicacion.ListarProductosServicio;
import nexus.aplicacion.ProductoMapper;
import nexus.dominio.EstadoProducto;
import nexus.dominio.Producto;
import nexus.dominio.TipoProducto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/productos")
public class ProductosController {

        private final CrearProductoServicio servicio;
        private final ConsultarProductoServicio consultarServicio;
        private final ConsultarEstadoCatalogoServicio consultaEstado;
        private final ListarProductosServicio listarServicio;
        private final ProductoMapper mapper;

        public ProductosController(
                        CrearProductoServicio servicio,
                        ConsultarProductoServicio consultarServicio,
                        ConsultarEstadoCatalogoServicio consultaEstado,
                        ListarProductosServicio listarServicio,
                        ProductoMapper mapper) {
                this.servicio = servicio;
                this.consultarServicio = consultarServicio;
                this.consultaEstado = consultaEstado;
                this.listarServicio = listarServicio;
                this.mapper = mapper;
        }

        /**
         * Listado publico y paginado del catalogo — R16, contrato 1.2.0.
         *
         * <p>Los limites de {@code page} y {@code size} son los del contrato y
         * los comprueba Bean Validation antes de entrar al metodo: un valor
         * fuera de rango no llega al servicio y responde 400 con Problem
         * Details (ver {@link ManejadorDeErrores}). Que estados y que orden se
         * listan lo decide {@link ListarProductosServicio}.
         *
         * <p>No choca con {@code GET /{id}} ni con {@code GET /estadisticas}:
         * esta es la ruta de la coleccion, sin sufijo.
         */
        @GetMapping
        public PaginaDeProductos listar(
                        @RequestParam(name = "page", defaultValue = "0")
                        @Min(value = 0, message = "page debe ser mayor o igual que 0")
                        int page,
                        @RequestParam(name = "size", defaultValue = "20")
                        @Min(value = 1, message = "size debe estar entre 1 y 50")
                        @Max(value = 50, message = "size debe estar entre 1 y 50")
                        int size,
                        @RequestParam(name = "tipo", required = false)
                        TipoProducto tipo,
                        @RequestParam(name = "estado", required = false)
                        EstadoProducto estado) {

                return listarServicio.listar(page, size, tipo, estado);
        }

        @GetMapping("/estadisticas")
        public ResumenCatalogo consultarEstado() {
                return consultaEstado.consultar();
        }

        @PostMapping
        public ResponseEntity<ProductoCreado> crear(
        @Valid @RequestBody SolicitudCrearProducto solicitud) {

                Producto producto = servicio.crear(solicitud);
                ProductoCreado respuesta = mapper.aRespuesta(producto);
                URI ubicacion = URI.create(
                        "/api/v1/productos/" + producto.id());

                return ResponseEntity
                        .created(ubicacion)
                        .body(respuesta);
        }

        @GetMapping("/{id}")
        public ResponseEntity<ProductoCreado> consultar(@PathVariable String id) {

                Producto producto = consultarServicio.consultar(id);

                return ResponseEntity.ok(mapper.aRespuesta(producto));
        }
}
