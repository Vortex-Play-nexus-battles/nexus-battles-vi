package nexus.configuracion;

import java.util.Set;

import nexus.aplicacion.Visibilidad;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * Traduce quien llama a la {@link Visibilidad} del catalogo — B4.
 *
 * <p>Los roles salen del token, traducidos por {@code ConversorRolesJwt}: un
 * servicio (rol SERVICIO, ADR-005) o un ADMINISTRADOR / SUPER_ADMINISTRADOR ven
 * todo; cualquier otro usuario autenticado, la vista de jugador; sin token, la
 * vista publica. Nada de esto sale del cuerpo ni de una cabecera propia.
 */
public final class VisibilidadDelLlamador {

        /** Quienes administran o integran el catalogo. */
        static final Set<String> ROLES_PRIVILEGIADOS = Set.of(
                "ROLE_SERVICIO", "ROLE_ADMINISTRADOR", "ROLE_SUPER_ADMINISTRADOR");

        private VisibilidadDelLlamador() {
        }

        public static Visibilidad de(Authentication autenticacion) {
                if (autenticacion == null
                                || autenticacion instanceof AnonymousAuthenticationToken
                                || !autenticacion.isAuthenticated()) {
                        return Visibilidad.PUBLICA;
                }
                boolean privilegiado = autenticacion.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch(ROLES_PRIVILEGIADOS::contains);
                return privilegiado ? Visibilidad.PRIVILEGIADA : Visibilidad.AUTENTICADA;
        }
}
