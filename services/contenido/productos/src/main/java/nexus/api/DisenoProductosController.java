package nexus.api;

import java.util.List;

import jakarta.validation.Valid;
import nexus.aplicacion.GestionarVersionesDisenoServicio;
import nexus.aplicacion.ProyeccionDeProductos;
import nexus.aplicacion.Visibilidad;
import nexus.dominio.Producto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Operaciones administrativas de HU-PRD-011. */
@RestController
@RequestMapping("/api/v1/productos/{productoId}/diseno")
public class DisenoProductosController {

        private final GestionarVersionesDisenoServicio versiones;
        private final ProyeccionDeProductos proyeccion;

        public DisenoProductosController(
                        GestionarVersionesDisenoServicio versiones,
                        ProyeccionDeProductos proyeccion) {
                this.versiones = versiones;
                this.proyeccion = proyeccion;
        }

        @GetMapping("/versiones")
        public List<VersionDisenoVista> consultarVersiones(@PathVariable String productoId) {
                return versiones.consultar(productoId);
        }

        @GetMapping("/plantilla")
        public PlantillaDisenoProducto exportar(@PathVariable String productoId) {
                return versiones.exportar(productoId);
        }

        @PostMapping("/importaciones")
        public ResponseEntity<ProductoCreado> importar(
                        Authentication autenticacion,
                        @PathVariable String productoId,
                        @Valid @RequestBody PlantillaDisenoProducto plantilla) {
                Producto producto = versiones.importar(productoId, plantilla, autenticacion.getName());
                return ResponseEntity.ok(proyeccion.proyectar(producto, Visibilidad.PRIVILEGIADA));
        }
}
