package nexus.inventario.configuracion;

import com.nexusbattles.comun.seguridad.IdentidadDelToken;
import nexus.inventario.aplicacion.IdentidadRequeridaException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

/**
 * De quien es el inventario sobre el que se opera — en un solo sitio.
 *
 * <p>Hasta ahora el propietario era lo que dijera la cabecera
 * {@code X-User-Name}, sin verificar: cualquiera podia leer, crear, equipar o
 * borrar en el inventario de otro con solo escribir su apodo. Ahora la
 * cabecera se acepta <b>solo cuando quien llama es un servicio autenticado</b>
 * (ADR-001/ADR-005: el token identifica al servicio, y el jugador afectado
 * viaja explicito como dato de la peticion). Cuando quien llama es un jugador,
 * el propietario es <b>el del token</b> y la cabecera se ignora.
 *
 * <p>Por que el apodo y no el {@code uid}: el inventario guarda
 * {@code propietarioId} por apodo desde HU-INV-001 y asi lo consultan
 * salas-partidas (ADR-004) y la semilla del E2E. Cambiar la clave de
 * propiedad al identificador estable de ADR-002 es una migracion de datos
 * del equipo de Contenido; este cambio cierra la suplantacion sin moverla.
 */
@Component
public class IdentidadDelLlamador {

    /** Cabecera con el propietario afectado, valida solo desde un servicio. */
    public static final String CABECERA_PROPIETARIO = "X-User-Name";

    static final String ROL_SERVICIO = "ROLE_SERVICIO";
    static final Set<String> ROLES_DE_USUARIO =
            Set.of("ROLE_JUGADOR", "ROLE_MODERADOR", "ROLE_ADMINISTRADOR", "ROLE_SUPER_ADMINISTRADOR");

    /**
     * Propietario del inventario sobre el que se opera.
     *
     * @param autenticacion la que dejo la cadena de seguridad
     * @param cabecera      valor de {@code X-User-Name}, o nulo
     * @return el apodo del jugador
     * @throws ResponseStatusException 401 sin autenticacion; 400 si un
     *                                 servicio no dice a que jugador afecta;
     *                                 403 si el token no es de usuario ni de
     *                                 servicio
     */
    public String propietario(Authentication autenticacion, String cabecera) {
        if (!(autenticacion instanceof JwtAuthenticationToken jwt)) {
            // Mismo problem detail (401 "Identidad requerida") que ya publicaba
            // el inventario cuando faltaba la identidad: el contrato no cambia.
            throw new IdentidadRequeridaException();
        }
        if (esUsuario(jwt)) {
            return IdentidadDelToken.apodoDe(jwt.getToken());
        }
        if (esServicio(jwt)) {
            if (cabecera == null || cabecera.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Un servicio debe indicar en " + CABECERA_PROPIETARIO + " a que jugador afecta.");
            }
            return cabecera.strip();
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El token no es de un jugador ni de un servicio.");
    }

    /** Si quien llama es un servicio (ADR-005) y no un jugador. */
    public boolean esServicio(Authentication autenticacion) {
        return autenticacion != null && autenticacion.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROL_SERVICIO::equals);
    }

    private static boolean esUsuario(Authentication autenticacion) {
        return autenticacion.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROLES_DE_USUARIO::contains);
    }
}
