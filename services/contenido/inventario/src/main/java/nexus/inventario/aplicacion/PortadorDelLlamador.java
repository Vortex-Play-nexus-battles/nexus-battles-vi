package nexus.inventario.aplicacion;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * El token con el que llamaron al inventario, para reenviarlo al catalogo — B4.
 *
 * <p>Desde productos 1.4.0 el catalogo tiene proyeccion publica: sin token, un
 * producto SUSPENDIDO no existe. El inventario si necesita verlo —un jugador
 * conserva lo que tenia aunque el producto se suspenda (7.2.1), y sus
 * estadisticas, su ficha y su equipo tienen que seguir resolviendose—, asi que
 * cada consulta al catalogo lleva la credencial de quien pidio: la del jugador
 * (el catalogo le deja ver un suspendido por su id) o la del servicio o
 * administrador (lo ve todo). Toda ruta del inventario exige token, asi que
 * siempre hay uno que reenviar; sin el, la consulta sale sin cabecera.
 *
 * <p>Es el patron que {@code InterceptorDePortadorDeServicio} ya contempla
 * ("hay llamadas que propagan deliberadamente otra credencial") y no necesita
 * una credencial de servicio propia del inventario, que hoy no tiene.
 */
public final class PortadorDelLlamador {

    private PortadorDelLlamador() {
    }

    /** El valor del token de la peticion en curso, si es un JWT. */
    public static Optional<String> actual() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion instanceof JwtAuthenticationToken jwt) {
            return Optional.of(jwt.getToken().getTokenValue());
        }
        return Optional.empty();
    }
}
