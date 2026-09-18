package com.nexusbattles.plataforma.salaspartidas.api;

import com.nexusbattles.plataforma.salaspartidas.aplicacion.ObtenerPartida;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * API del combate — RF-JUE-017.
 *
 * <p>Una sola operacion, y con un proposito acotado: pintar la vista la primera
 * vez y ponerse al dia tras una caida del canal. El avance turno a turno viaja
 * por WebSocket ({@code /tema/partidas/{id}}), no por aqui; si la vista
 * consultara esta ruta en bucle, el canal sobraria.
 */
@RestController
@RequestMapping("/api/v1/partidas")
public class PartidasController {

    private final ObtenerPartida obtenerPartida;

    PartidasController(ObtenerPartida obtenerPartida) {
        this.obtenerPartida = obtenerPartida;
    }

    /**
     * Estado completo de la partida.
     *
     * <p>404 si no existe, con problem details, igual que el resto del servicio.
     */
    @GetMapping("/{idPartida}")
    public PartidaResponse obtener(@PathVariable UUID idPartida) {
        return PartidaResponse.desde(obtenerPartida.ejecutar(idPartida));
    }
}
