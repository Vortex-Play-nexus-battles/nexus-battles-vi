package nexus.misiones.api;

import jakarta.validation.Valid;
import nexus.misiones.aplicacion.GestionarEstrategias;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Estrategias guardadas por heroe (7.8.5 y 7.8.12, «configuraciones de rotaciones guardadas»). */
@RestController
@RequestMapping("/api/v1/misiones/estrategias")
class EstrategiasController {

    private final GestionarEstrategias estrategias;
    private final VistasDeMisiones vistas;

    EstrategiasController(GestionarEstrategias estrategias, VistasDeMisiones vistas) {
        this.estrategias = estrategias;
        this.vistas = vistas;
    }

    @GetMapping("/{heroeId}")
    Respuestas.Estrategia consultar(@AuthenticationPrincipal Jwt token, @PathVariable String heroeId) {
        return vistas.estrategia(estrategias.consultar(MisionesController.jugador(token), heroeId));
    }

    @PutMapping("/{heroeId}")
    Respuestas.Estrategia guardar(@AuthenticationPrincipal Jwt token, @PathVariable String heroeId,
                                  @Valid @RequestBody Solicitudes.Estrategia solicitud) {
        return vistas.estrategia(estrategias.guardar(MisionesController.jugador(token), heroeId,
                Solicitudes.comoListas(solicitud.rotaciones())));
    }
}
