package com.nexusbattles.plataforma.salaspartidas.api;

import com.nexusbattles.comun.seguridad.IdentidadDelToken;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.MisPartidas;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.ObtenerPartida;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;

/**
 * API del combate — RF-JUE-017.
 *
 * <p>Pintar la vista la primera vez y ponerse al dia tras una caida del canal.
 * El avance turno a turno viaja por WebSocket ({@code /tema/partidas/{id}}), no
 * por aqui; si la vista consultara esta ruta en bucle, el canal sobraria.
 *
 * <p>Desde 1.7.0 (B7) una partida solo la ven quienes la juegan y los roles de
 * operacion, y {@code /partidas/mias} da el historial de quien pregunta. En los
 * dos casos el jugador sale del token, nunca de la ruta.
 */
@RestController
@RequestMapping("/api/v1/partidas")
public class PartidasController {

    /** Roles que ya podian leer partidas para atender reportes (ver SecurityConfig). */
    private static final Set<String> ROLES_DE_OPERACION =
            Set.of("ROLE_MODERADOR", "ROLE_ADMINISTRADOR", "ROLE_SUPER_ADMINISTRADOR");

    private final ObtenerPartida obtenerPartida;
    private final MisPartidas misPartidas;
    private final Clock reloj;

    PartidasController(ObtenerPartida obtenerPartida, MisPartidas misPartidas) {
        this.obtenerPartida = obtenerPartida;
        this.misPartidas = misPartidas;
        this.reloj = Clock.systemUTC();
    }

    /**
     * Estado completo de la partida.
     *
     * <p>404 si no existe y 403 {@code partida-ajena} si quien pregunta no la
     * juega, con problem details, igual que el resto del servicio.
     */
    @GetMapping("/{idPartida}")
    public PartidaResponse obtener(@PathVariable UUID idPartida, @AuthenticationPrincipal Jwt token,
                                   Authentication autenticacion) {
        return PartidaResponse.desde(
                obtenerPartida.ejecutar(idPartida, IdentidadDelToken.idDe(token), deOperacion(autenticacion)),
                reloj.instant());
    }

    /** Historial de partidas de quien firma el token, de la mas reciente a la mas antigua. */
    @GetMapping("/mias")
    public MisPartidas.Pagina mias(@RequestParam(defaultValue = "0") int pagina,
                                   @RequestParam(defaultValue = "16") int tamano,
                                   @AuthenticationPrincipal Jwt token) {
        return misPartidas.ejecutar(IdentidadDelToken.idDe(token), pagina, tamano);
    }

    private static boolean deOperacion(Authentication autenticacion) {
        return autenticacion != null && autenticacion.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROLES_DE_OPERACION::contains);
    }
}
