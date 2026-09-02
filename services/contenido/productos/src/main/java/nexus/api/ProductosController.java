package nexus.api;

import java.net.URI;

import jakarta.validation.Valid;
import nexus.aplicacion.ConsultarProductoServicio;
import nexus.aplicacion.CrearProductoServicio;
import nexus.aplicacion.ConsultarEstadoCatalogoServicio;
import nexus.aplicacion.ModificarProductoServicio;
import nexus.aplicacion.ProductoMapper;
import nexus.dominio.Producto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/productos")
public class ProductosController {

        private final CrearProductoServicio servicio;
        private final ConsultarProductoServicio consultarServicio;
        private final ConsultarEstadoCatalogoServicio consultaEstado;
        private final ModificarProductoServicio modificarServicio;
        private final ProductoMapper mapper;

        public ProductosController(
                        CrearProductoServicio servicio,
                        ConsultarProductoServicio consultarServicio,
                        ConsultarEstadoCatalogoServicio consultaEstado,
                        ModificarProductoServicio modificarServicio,
                        ProductoMapper mapper) {
                this.servicio = servicio;
                this.consultarServicio = consultarServicio;
                this.consultaEstado = consultaEstado;
                this.modificarServicio = modificarServicio;
                this.mapper = mapper;
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

        @PatchMapping("/{id}")
        public ResponseEntity<ProductoCreado> modificar(
                        @PathVariable String id,
                        @Valid @RequestBody SolicitudModificarProducto cambios) {

                Producto producto = modificarServicio.modificar(id, cambios);

                return ResponseEntity.ok(mapper.aRespuesta(producto));
        }
}
