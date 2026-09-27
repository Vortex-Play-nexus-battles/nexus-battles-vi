package nexus.inventario.configuracion;

import com.nexusbattles.comun.seguridad.CadenaDeSeguridad;
import com.nexusbattles.comun.seguridad.ConversorRolesJwt;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import nexus.inventario.aplicacion.IdentidadRequeridaException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * Seguridad del inventario.
 *
 * <p>Clases de rutas:
 * <ul>
 *   <li><b>Del jugador</b> (vitrina, busqueda, crear/modificar/borrar,
 *       equipamiento, estadisticas): exigen un usuario autenticado o un
 *       servicio con credencial. Quien es el propietario lo decide
 *       {@link IdentidadDelLlamador}: el del token si llama un jugador, el de
 *       {@code X-User-Name} si llama un servicio (salas-partidas al verificar
 *       el heroe de cada participante, ADR-004). Antes eran {@code permitAll}
 *       y la cabecera se creia sin mas.</li>
 *   <li><b>De propiedad</b> (B4: {@code POST /elementos} y {@code POST
 *       /entregas}): solo un servicio o un ADMINISTRADOR/SUPER_ADMINISTRADOR.
 *       Un jugador que intenta crearse un elemento recibe 403.</li>
 *   <li><b>De subastas</b> (consulta por id, bloqueo y liberacion): solo el
 *       servicio de subastas, por su {@code azp}, como desde HU-INV-010.</li>
 *   <li><b>De misiones</b> (1.6.0, B9: bloqueo del heroe en mision y su
 *       liberacion): solo el servicio de misiones, con rol SERVICIO y su
 *       {@code azp}. La consulta por id la comparten subastas y misiones.</li>
 *   <li><b>Actuator</b>: abierto para la sonda de salud (regla 3).</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SeguridadConfig {

    /** Los que tienen inventario propio, mas los servicios que actuan por ellos. */
    static final String[] ROLES_DEL_INVENTARIO =
            {"JUGADOR", "MODERADOR", "ADMINISTRADOR", "SUPER_ADMINISTRADOR", "SERVICIO"};

    /**
     * B4: quienes pueden dar la propiedad de un producto ({@code POST
     * /elementos} y {@code POST /entregas}). Un jugador la recibe, no se la da.
     */
    static final String[] ROLES_QUE_DAN_PROPIEDAD = {"SERVICIO", "ADMINISTRADOR", "SUPER_ADMINISTRADOR"};

    @Bean
    public ConversorRolesJwt conversorRolesJwt() {
        return new ConversorRolesJwt();
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            ConversorRolesJwt conversor,
            @Value("${integraciones.subastas.client-id}") String subastasClientId,
            @Value("${integraciones.misiones.client-id:misiones}") String misionesClientId) throws Exception {
        CadenaDeSeguridad.aplicarBase(http, conversor);
        // La cadena base deja el 401 de Spring (cuerpo vacio). El contrato de
        // inventario promete el problem detail "Identidad requerida" tambien
        // cuando no hay token, asi que el punto de entrada lo escribe aqui.
        http.exceptionHandling(excepciones -> excepciones
                .authenticationEntryPoint(SeguridadConfig::responderIdentidadRequerida)
                // B4: el 403 de la cadena tambien con cuerpo (problem detail);
                // el de Spring sale vacio y el consumidor no sabe por que.
                .accessDeniedHandler(SeguridadConfig::responderAccesoDenegado));
        AuthorizationManager<RequestAuthorizationContext> soloSubastas = (authentication, context) -> {
            if (authentication.get() instanceof JwtAuthenticationToken jwt) {
                return new AuthorizationDecision(
                        subastasClientId.equals(jwt.getToken().getClaimAsString("azp")));
            }
            return new AuthorizationDecision(false);
        };
        // 1.6.0 (B9): el heroe en mision lo bloquea y lo libera SOLO el servicio
        // de misiones, con su credencial de servicio (rol SERVICIO y su azp).
        AuthorizationManager<RequestAuthorizationContext> soloMisiones = (authentication, context) ->
                new AuthorizationDecision(esServicioConAzp(authentication.get(), misionesClientId));
        // La consulta interna por id la usan subastas (HU-INV-010) y misiones.
        AuthorizationManager<RequestAuthorizationContext> subastasOMisiones = (authentication, context) ->
                new AuthorizationDecision(soloSubastas.authorize(authentication, context).isGranted()
                        || esServicioConAzp(authentication.get(), misionesClientId));
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers(HttpMethod.PUT, "/api/v1/inventario/elementos/*/bloqueo-mision")
                .access(soloMisiones)
                .requestMatchers(HttpMethod.POST, "/api/v1/inventario/elementos/*/bloqueo-mision/*/liberacion")
                .access(soloMisiones)
                // Subastas (HU-INV-010): por azp, antes que el comodin de elementos.
                .requestMatchers(HttpMethod.PUT, "/api/v1/inventario/elementos/*/bloqueo-subasta")
                .access(soloSubastas)
                .requestMatchers(HttpMethod.DELETE, "/api/v1/inventario/elementos/*/bloqueo-subasta/*")
                .access(soloSubastas)
                // Transferencia de propiedad al cerrar una subasta (HU-SUB-004).
                // Mismo criterio que el bloqueo: solo ms-subastas, por azp. Cambiar
                // de dueno un elemento es mas delicado que bloquearlo, asi que no
                // puede caer en el comodin de "cualquier servicio autenticado".
                .requestMatchers(HttpMethod.POST, "/api/v1/inventario/elementos/*/transferencias")
                .access(soloSubastas)
                .requestMatchers(HttpMethod.GET, "/api/v1/inventario/elementos/busqueda")
                .hasAnyRole(ROLES_DEL_INVENTARIO)
                .requestMatchers(HttpMethod.GET, "/api/v1/inventario/elementos/*")
                .access(subastasOMisiones)
                // B4: la propiedad solo llega por canales validos. Crear un
                // elemento a mano y entregar productos son de un servicio (la
                // compra, el cofre, el paquete inicial de ms-identidad...) o de
                // un administrador; un JUGADOR ya no se crea objetos gratis.
                .requestMatchers(HttpMethod.POST, "/api/v1/inventario/elementos")
                .hasAnyRole(ROLES_QUE_DAN_PROPIEDAD)
                .requestMatchers(HttpMethod.POST, "/api/v1/inventario/entregas")
                .hasAnyRole(ROLES_QUE_DAN_PROPIEDAD)
                // Todo lo demas del inventario: jugadores y servicios autenticados.
                .requestMatchers("/api/v1/inventario/**").hasAnyRole(ROLES_DEL_INVENTARIO)
                .anyRequest().authenticated());
        return http.build();
    }

    /** Un token de servicio (rol SERVICIO) cuyo {@code azp} es {@code clientId}. */
    static boolean esServicioConAzp(org.springframework.security.core.Authentication autenticacion,
                                    String clientId) {
        if (!(autenticacion instanceof JwtAuthenticationToken jwt)) {
            return false;
        }
        boolean servicio = jwt.getAuthorities().stream()
                .anyMatch(autoridad -> "ROLE_SERVICIO".equals(autoridad.getAuthority()));
        return servicio && clientId.equals(jwt.getToken().getClaimAsString("azp"));
    }

    static void responderIdentidadRequerida(
            HttpServletRequest solicitud,
            HttpServletResponse respuesta,
            AuthenticationException error) throws IOException {
        respuesta.setStatus(HttpStatus.UNAUTHORIZED.value());
        respuesta.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        respuesta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        respuesta.getWriter().write("""
                {"type":"about:blank","title":"Identidad requerida","status":401,"detail":"%s","instance":"%s"}"""
                .formatted(new IdentidadRequeridaException().getMessage(), solicitud.getRequestURI()));
    }

    static void responderAccesoDenegado(
            HttpServletRequest solicitud,
            HttpServletResponse respuesta,
            AccessDeniedException error) throws IOException {
        respuesta.setStatus(HttpStatus.FORBIDDEN.value());
        respuesta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        respuesta.getWriter().write("""
                {"type":"about:blank","title":"Acceso denegado","status":403,"detail":"%s","instance":"%s"}"""
                .formatted("Tu credencial no permite esta operacion del inventario.", solicitud.getRequestURI()));
    }
}
