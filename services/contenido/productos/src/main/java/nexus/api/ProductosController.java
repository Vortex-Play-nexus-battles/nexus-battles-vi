package nexus.api;

import java.net.URI;

import jakarta.validation.Valid;
import nexus.aplicacion.CrearProductoServicio;
import nexus.aplicacion.ProductoMapper;
import nexus.dominio.Producto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/productos")
public class ProductosController {

        private final CrearProductoServicio servicio;
        private final ProductoMapper mapper;

        public ProductosController(
                        CrearProductoServicio servicio,
                        ProductoMapper mapper) {
                this.servicio = servicio;
                this.mapper = mapper;
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
}
