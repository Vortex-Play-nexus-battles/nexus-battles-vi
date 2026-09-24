package nexus.configuracion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.nexusbattles.comun.seguridad.ConversorRolesJwt;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SeguridadConfig {

        @Bean
        public ConversorRolesJwt conversorRolesJwt() {
                return new ConversorRolesJwt();
        }

        @Bean
        public SecurityFilterChain cadenaDeSeguridad(
                        HttpSecurity http,
                        ConversorRolesJwt convertidorRoles) throws Exception {

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
                                // Los recursos estáticos no contienen datos protegidos. La
                                // autorización se mantiene obligatoria en la API de creación.
                                .requestMatchers(
                                        "/contenido/productos/**",
                                        "/comun/**")
                                .permitAll()
                                .requestMatchers(HttpMethod.POST, "/api/v1/productos")
                                .hasAnyRole("ADMINISTRADOR", "SUPER_ADMINISTRADOR")
                                .requestMatchers(HttpMethod.GET, "/api/v1/productos/estadisticas")
                                .authenticated()
                                // Lectura publica del catalogo: el detalle por id y, desde
                                // R16 (contrato 1.2.0), el listado paginado de la coleccion
                                // que proyecta la vitrina de ms-ecommerce. Solo GET: el POST
                                // de la misma ruta sigue exigiendo administrador (arriba), y
                                // /estadisticas va antes para no quedar cubierta por {id}.
                                .requestMatchers(
                                        HttpMethod.GET,
                                        "/api/v1/productos",
                                        "/api/v1/productos/{id}")
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

        // R9.7 — aqui vivia `convertidorRolesKeycloak`, que leia los roles
        // UNICAMENTE de `realm_access.roles`, la forma de Keycloak.
        //
        // El emisor real es ms-identidad (ADR-002 / ADR-005) y **no** emite ese
        // bloque: pone el rol en el claim `rol`, en singular, y el identificador
        // estable en `uid`. Con el conversor anterior, un token legitimo de
        // ms-identidad se autenticaba —la firma es valida— pero llegaba con
        // CERO authorities, asi que `POST /api/v1/productos` habria contestado
        // 403 a un administrador de verdad. Apuntar al JWKS correcto, por si
        // solo, no habria arreglado nada: lo habria cambiado de 401 a 403.
        //
        // `ConversorRolesJwt` (shared/libs/plataforma-seguridad) entiende las
        // DOS formas —`rol` de ms-identidad y `realm_access.roles` de
        // Keycloak—, asi que la coleccion de Postman de jwks-dev, que firma
        // tokens con forma de Keycloak, sigue funcionando. Ademas fija el
        // principal en `uid` cuando existe, como manda ADR-002.

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
