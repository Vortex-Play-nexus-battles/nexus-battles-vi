package nexus.api;

import java.net.URI;

import com.nexusbattles.comun.seguridad.servicio.ActorDeServicio;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import nexus.aplicacion.AdquirirProductoServicio;
import nexus.aplicacion.ConsultarEstadoCatalogoServicio;
import nexus.aplicacion.ConsultarProductoServicio;
import nexus.aplicacion.CrearProductoServicio;
import nexus.aplicacion.DisponibilidadDelCatalogoServicio;
import nexus.aplicacion.ListarProductosServicio;
import nexus.aplicacion.ModificarProductoServicio;
import nexus.aplicacion.ProyeccionDeProductos;
import nexus.aplicacion.Visibilidad;
import nexus.configuracion.VisibilidadDelLlamador;
import nexus.dominio.EstadoProducto;
import nexus.dominio.Producto;
import nexus.dominio.TipoProducto;
import nexus.productos.dominio.EstadoAdquisicion;
import nexus.productos.dominio.ResultadoAdquisicion;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
        private final ModificarProductoServicio modificarServicio;
        private final DisponibilidadDelCatalogoServicio disponibilidad;
        private final AdquirirProductoServicio adquisiciones;
        private final ProyeccionDeProductos proyeccion;

        public ProductosController(
                        CrearProductoServicio servicio,
                        ConsultarProductoServicio consultarServicio,
                        ConsultarEstadoCatalogoServicio consultaEstado,
                        ListarProductosServicio listarServicio,
                        ModificarProductoServicio modificarServicio,
                        DisponibilidadDelCatalogoServicio disponibilidad,
                        AdquirirProductoServicio adquisiciones,
                        ProyeccionDeProductos proyeccion) {
                this.servicio = servicio;
                this.consultarServicio = consultarServicio;
                this.consultaEstado = consultaEstado;
                this.listarServicio = listarServicio;
                this.modificarServicio = modificarServicio;
                this.disponibilidad = disponibilidad;
                this.adquisiciones = adquisiciones;
                this.proyeccion = proyeccion;
        }

        /**
         * Listado publico y paginado del catalogo — R16, contrato 1.2.0.
         *
         * <p>Los limites de {@code page} y {@code size} son los del contrato y
         * los comprueba Bean Validation antes de entrar al metodo: un valor
         * fuera de rango no llega al servicio y responde 400 con Problem
         * Details (ver {@link ManejadorDeErrores}). Que estados, que orden y que
         * campos se listan lo decide {@link ListarProductosServicio}, segun
         * quien llama (B4, contrato 1.4.0: sin token, la proyeccion publica).
         *
         * <p>No choca con {@code GET /{id}} ni con {@code GET /estadisticas}:
         * esta es la ruta de la coleccion, sin sufijo.
         */
        @GetMapping
        public PaginaDeProductos listar(
                        Authentication autenticacion,
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

                return listarServicio.listar(page, size, tipo, estado, VisibilidadDelLlamador.de(autenticacion));
        }

        @GetMapping("/estadisticas")
        public ResumenCatalogo consultarEstado() {
                return consultaEstado.consultar();
        }

        @PostMapping
        public ResponseEntity<ProductoCreado> crear(
        @Valid @RequestBody SolicitudCrearProducto solicitud) {

                Producto producto = servicio.crear(solicitud);
                ProductoCreado respuesta = proyeccion.proyectar(producto, Visibilidad.PRIVILEGIADA);
                URI ubicacion = URI.create(
                        "/api/v1/productos/" + producto.id());

                return ResponseEntity
                        .created(ubicacion)
                        .body(respuesta);
        }

        @GetMapping("/{id}")
        public ResponseEntity<ProductoCreado> consultar(
                        Authentication autenticacion,
                        @PathVariable String id) {

                Visibilidad visibilidad = VisibilidadDelLlamador.de(autenticacion);
                Producto producto = consultarServicio.consultar(id, visibilidad);

                return ResponseEntity.ok(proyeccion.proyectar(producto, visibilidad));
        }

        /**
         * HU-PRD-003. El autor es el principal del token (el {@code uid} del
         * administrador, ADR-002): queda en el respaldo y en el producto.
         */
        @PatchMapping("/{id}")
        public ResponseEntity<ProductoCreado> modificar(
                        Authentication autenticacion,
                        @PathVariable String id,
                        @Valid @RequestBody SolicitudModificarProducto cambios) {

                Producto producto = modificarServicio.modificar(id, cambios, autenticacion.getName());

                return ResponseEntity.ok(proyeccion.proyectar(producto, Visibilidad.PRIVILEGIADA));
        }

        /** HU-PRD-004 (B4): suspension logica, solo ADMINISTRADOR y SUPER_ADMINISTRADOR. */
        @PutMapping("/{id}/suspender")
        public EstadoDisponibilidadProducto suspender(
                        Authentication autenticacion,
                        @PathVariable String id) {
                return EstadoDisponibilidadProducto.de(disponibilidad.suspender(id, autenticacion.getName()));
        }

        /** HU-PRD-004 (B4): reactivacion al estado anterior, solo ADMINISTRADOR y SUPER_ADMINISTRADOR. */
        @PutMapping("/{id}/reactivar")
        public EstadoDisponibilidadProducto reactivar(
                        Authentication autenticacion,
                        @PathVariable String id) {
                return EstadoDisponibilidadProducto.de(disponibilidad.reactivar(id, autenticacion.getName()));
        }

        /**
         * HU-PRD-002 (B4): reserva de una unidad de tiraje, solo con token de
         * servicio. 200 si se reservo; 409 con el mismo cuerpo si el producto
         * esta agotado o suspendido, que es como lo publica el contrato.
         */
        @PostMapping("/{id}/adquisiciones")
        public ResponseEntity<ResultadoAdquisicion> adquirir(
                        Authentication autenticacion,
                        @RequestHeader("Idempotency-Key")
                        @NotBlank(message = "Idempotency-Key no puede estar vacia")
                        @Size(max = 100, message = "Idempotency-Key admite hasta 100 caracteres")
                        String clave,
                        @PathVariable String id) {

                String solicitante = ActorDeServicio.desde(autenticacion).orElse(autenticacion.getName());
                ResultadoAdquisicion resultado = adquisiciones.adquirir(id, clave, solicitante);
                HttpStatus estado = resultado.estado() == EstadoAdquisicion.ACEPTADA
                        ? HttpStatus.OK
                        : HttpStatus.CONFLICT;
                return ResponseEntity.status(estado).body(resultado);
        }
}
