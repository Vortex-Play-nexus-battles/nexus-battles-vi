package nexus.api;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import nexus.aplicacion.GestionarBannersServicio;
import nexus.dominio.Banner;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Banners de la página de inicio — HU-PRD-013. SA-18: quien publica, edita o
 * retira es el principal del token (el {@code uid}, ADR-002), y cada banner
 * tiene su historial.
 */
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

        @GetMapping
        public List<RespuestaBanner> consultarTodos() {
                return servicio.consultarTodos().stream()
                        .map(RespuestaBanner::desde)
                        .toList();
        }

        @PostMapping
        public ResponseEntity<RespuestaBanner> crear(
                Authentication autenticacion,
                @Valid @RequestBody SolicitudBanner solicitud) {
                Banner creado = servicio.crear(solicitud, autenticacion.getName());
                return ResponseEntity
                        .created(URI.create("/api/v1/banners/" + creado.id()))
                        .body(RespuestaBanner.desde(creado));
        }

        @PutMapping("/{id}")
        public RespuestaBanner editar(
                Authentication autenticacion,
                @PathVariable String id,
                @Valid @RequestBody SolicitudBanner solicitud) {
                return RespuestaBanner.desde(servicio.editar(id, solicitud, autenticacion.getName()));
        }

        /** Retiro lógico; {@code motivo} opcional (SA-18), que queda en el historial. */
        @DeleteMapping("/{id}")
        public ResponseEntity<Void> retirar(
                Authentication autenticacion,
                @PathVariable String id,
                @RequestParam(name = "motivo", required = false)
                @Size(max = 500, message = "motivo admite como máximo 500 caracteres") String motivo) {
                servicio.retirar(id, autenticacion.getName(), motivo);
                return ResponseEntity.noContent().build();
        }

        /** SA-18: lo que se hizo con el banner, de lo más reciente a lo más antiguo, sin la IP. */
        @GetMapping("/{id}/historial")
        public List<OperacionDelCatalogoVista> historial(@PathVariable String id) {
                return servicio.historial(id).stream()
                        .map(OperacionDelCatalogoVista::de)
                        .toList();
        }
}
