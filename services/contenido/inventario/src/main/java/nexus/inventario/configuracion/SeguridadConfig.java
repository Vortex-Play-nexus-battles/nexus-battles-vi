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
 * <p>Tres clases de rutas:
 * <ul>
 *   <li><b>Del jugador</b> (vitrina, busqueda, crear/modificar/borrar,
 *       equipamiento, estadisticas): exigen un usuario autenticado o un
 *       servicio con credencial. Quien es el propietario lo decide
 *       {@link IdentidadDelLlamador}: el del token si llama un jugador, el de
 *       {@code X-User-Name} si llama un servicio (salas-partidas al verificar
 *       el heroe de cada participante, ADR-004). Antes eran {@code permitAll}
 *       y la cabecera se creia sin mas.</li>
 *   <li><b>De subastas</b> (consulta por id, bloqueo y liberacion): solo el
 *       servicio de subastas, por su {@code azp}, como desde HU-INV-010.</li>
 *   <li><b>Actuator</b>: abierto para la sonda de salud (regla 3).</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SeguridadConfig {

    /** Los que tienen inventario propio, mas los servicios que actuan por ellos. */
    static final String[] ROLES_DEL_INVENTARIO =
            {"JUGADOR", "MODERADOR", "ADMINISTRADOR", "SUPER_ADMINISTRADOR", "SERVICIO"};

    @Bean
    public ConversorRolesJwt conversorRolesJwt() {
        return new ConversorRolesJwt();
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            ConversorRolesJwt conversor,
            @Value("${integraciones.subastas.client-id}") String subastasClientId) throws Exception {
        CadenaDeSeguridad.aplicarBase(http, conversor);
        // La cadena base deja el 401 de Spring (cuerpo vacio). El contrato de
        // inventario promete el problem detail "Identidad requerida" tambien
        // cuando no hay token, asi que el punto de entrada lo escribe aqui.
        http.exceptionHandling(excepciones -> excepciones
                .authenticationEntryPoint(SeguridadConfig::responderIdentidadRequerida));
        AuthorizationManager<RequestAuthorizationContext> soloSubastas = (authentication, context) -> {
            if (authentication.get() instanceof JwtAuthenticationToken jwt) {
                return new AuthorizationDecision(
                        subastasClientId.equals(jwt.getToken().getClaimAsString("azp")));
            }
            return new AuthorizationDecision(false);
        };
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                // Subastas (HU-INV-010): por azp, antes que el comodin de elementos.
                .requestMatchers(HttpMethod.PUT, "/api/v1/inventario/elementos/*/bloqueo-subasta")
                .access(soloSubastas)
                .requestMatchers(HttpMethod.DELETE, "/api/v1/inventario/elementos/*/bloqueo-subasta/*")
                .access(soloSubastas)
                .requestMatchers(HttpMethod.GET, "/api/v1/inventario/elementos/busqueda")
                .hasAnyRole(ROLES_DEL_INVENTARIO)
                .requestMatchers(HttpMethod.GET, "/api/v1/inventario/elementos/*")
                .access(soloSubastas)
                // Todo lo demas del inventario: jugadores y servicios autenticados.
                .requestMatchers("/api/v1/inventario/**").hasAnyRole(ROLES_DEL_INVENTARIO)
                .anyRequest().authenticated());
        return http.build();
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
}
