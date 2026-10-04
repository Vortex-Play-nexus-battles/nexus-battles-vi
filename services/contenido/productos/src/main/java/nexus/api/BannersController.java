package nexus.api;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;
import nexus.aplicacion.GestionarBannersServicio;
import nexus.dominio.Banner;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/banners")
public class BannersController {

        private final GestionarBannersServicio servicio;

        public BannersController(GestionarBannersServicio servicio) {
                this.servicio = servicio;
        }

        @GetMapping("/vigentes")
        public List<RespuestaBanner> consultarVigentes() {
                return servicio.consultarVigentes().stream()
                        .map(RespuestaBanner::desde)
                        .toList();
        }

        @PostMapping
        public ResponseEntity<RespuestaBanner> crear(
                @Valid @RequestBody SolicitudBanner solicitud) {
                Banner creado = servicio.crear(solicitud);
                return ResponseEntity
                        .created(URI.create("/api/v1/banners/" + creado.id()))
                        .body(RespuestaBanner.desde(creado));
        }

        @PutMapping("/{id}")
        public RespuestaBanner editar(
                @PathVariable String id,
                @Valid @RequestBody SolicitudBanner solicitud) {
                return RespuestaBanner.desde(servicio.editar(id, solicitud));
        }

        @DeleteMapping("/{id}")
        public ResponseEntity<Void> retirar(@PathVariable String id) {
                servicio.retirar(id);
                return ResponseEntity.noContent().build();
        }
}
