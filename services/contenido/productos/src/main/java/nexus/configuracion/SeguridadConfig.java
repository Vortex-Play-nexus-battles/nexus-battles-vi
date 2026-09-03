package nexus.configuracion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SeguridadConfig {

        @Bean
        public SecurityFilterChain cadenaDeSeguridad(
                        HttpSecurity http,
                        JwtAuthenticationConverter convertidorRoles) throws Exception {

                http
                        // La API usa exclusivamente tokens Bearer en el encabezado y no
                        // autenticacion basada en cookies. Se excluye solo la API versionada
                        // y se conserva la proteccion CSRF para cualquier otra superficie web.
                        .csrf(csrf -> csrf
                                .ignoringRequestMatchers("/api/v1/**"))
                        .sessionManagement(session -> session
                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                        .authorizeHttpRequests(autorizacion -> autorizacion
                                .requestMatchers(
                                        "/actuator/health/**",
                                        "/actuator/info",
                                        "/actuator/prometheus",
                                        "/v3/api-docs/**")
                                .permitAll()
                                .requestMatchers(HttpMethod.POST, "/api/v1/productos")
                                .hasAnyRole("ADMINISTRADOR", "SUPER_ADMINISTRADOR")
                                .requestMatchers(HttpMethod.GET, "/api/v1/productos/{id}")
                                .permitAll()
                                .anyRequest()
                                .authenticated())
                        .exceptionHandling(excepciones -> excepciones
                                .authenticationEntryPoint((solicitud, respuesta, excepcion) -> {
                                        respuesta.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
                                        escribirProblema(
                                                solicitud,
                                                respuesta,
                                                HttpServletResponse.SC_UNAUTHORIZED,
                                                "No autenticado",
                                                "Se requiere un token Bearer válido",
                                                "urn:nexus:problema:no-autenticado");
                                })
                                .accessDeniedHandler((solicitud, respuesta, excepcion) ->
                                        escribirProblema(
                                                solicitud,
                                                respuesta,
                                                HttpServletResponse.SC_FORBIDDEN,
                                                "Acceso denegado",
                                                "No tienes permiso para realizar esta acción",
                                                "urn:nexus:problema:acceso-denegado")))
                        .oauth2ResourceServer(oauth2 -> oauth2
                                .jwt(jwt -> jwt
                                        .jwtAuthenticationConverter(convertidorRoles)));

                return http.build();
        }

        @Bean
        public JwtAuthenticationConverter convertidorRolesKeycloak() {
                JwtGrantedAuthoritiesConverter convertidorPermisos =
                        new JwtGrantedAuthoritiesConverter();

                JwtAuthenticationConverter convertidor =
                        new JwtAuthenticationConverter();

                convertidor.setJwtGrantedAuthoritiesConverter(jwt -> {
                        Collection<GrantedAuthority> autoridades = new ArrayList<>();

                        Collection<GrantedAuthority> permisos =
                                convertidorPermisos.convert(jwt);

                        if (permisos != null) {
                                autoridades.addAll(permisos);
                        }

                        Map<String, Object> accesoAlReino =
                                jwt.getClaimAsMap("realm_access");

                        if (accesoAlReino != null
                                        && accesoAlReino.get("roles") instanceof Collection<?> roles) {
                                roles.stream()
                                        .map(Object::toString)
                                        .map(rol -> new SimpleGrantedAuthority("ROLE_" + rol))
                                        .forEach(autoridades::add);
                        }

                        return autoridades;
                });

                return convertidor;
        }

        private static void escribirProblema(
                        HttpServletRequest solicitud,
                        HttpServletResponse respuesta,
                        int estado,
                        String titulo,
                        String detalle,
                        String tipo) throws IOException {

                respuesta.setStatus(estado);
                respuesta.setCharacterEncoding(StandardCharsets.UTF_8.name());
                respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                respuesta.getWriter().write("""
                        {"type":"%s","title":"%s","status":%d,"detail":"%s","instance":"%s"}
                        """.formatted(
                                tipo,
                                titulo,
                                estado,
                                detalle,
                                solicitud.getRequestURI()));
        }
}
