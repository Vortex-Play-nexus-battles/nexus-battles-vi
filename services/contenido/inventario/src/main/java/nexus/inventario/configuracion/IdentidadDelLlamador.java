package nexus.inventario.configuracion;

import com.nexusbattles.comun.seguridad.IdentidadDelToken;
import nexus.inventario.aplicacion.IdentidadRequeridaException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
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
 * <p>Por que el identificador estable ({@code uid}, o {@code sub} en UUID) y
 * no el apodo: el apodo es mutable y puede reasignarse; atar la propiedad a
 * el deja inventarios huerfanos cuando alguien se renombra y hereda objetos
 * ajenos cuando otro toma un apodo liberado. Es la misma clave que exige
 * HU-INV-010 ({@code propietarioUid}) y la que ya usan salas-partidas y
 * subastas para recordar a la gente. Decision del equipo de Contenido,
 * 21-sep-2026 (ver decisiones.md): los inventarios guardados por apodo se
 * atienden con la respuesta 409 "pendiente de migracion" de HU-INV-010.
 * Un servicio con credencial debe poner en {@code X-User-Name} ese mismo
 * identificador estable del jugador afectado.
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
     * @return el identificador estable del jugador (UUID en texto)
     * @throws ResponseStatusException 400 si un servicio no dice a que
     *                                 jugador afecta; 403 si el token no es
     *                                 de usuario ni de servicio, o si es de
     *                                 usuario y no trae un identificador
     *                                 estable
     * @throws IdentidadRequeridaException sin autenticacion (401)
     */
    public String propietario(Authentication autenticacion, String cabecera) {
        if (!(autenticacion instanceof JwtAuthenticationToken jwt)) {
            // Mismo problem detail (401 "Identidad requerida") que ya publicaba
            // el inventario cuando faltaba la identidad: el contrato no cambia.
            throw new IdentidadRequeridaException();
        }
        if (esUsuario(jwt)) {
            return identificadorEstableDe(jwt.getToken());
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
    private static String identificadorEstableDe(Jwt token) {
        try {
            return IdentidadDelToken.idDe(token).toString();
        } catch (IllegalArgumentException sinIdentificador) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "El token del jugador no trae un identificador estable (uid, o sub en UUID).");
        }
    }

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
