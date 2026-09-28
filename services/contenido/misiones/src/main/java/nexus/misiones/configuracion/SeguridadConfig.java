package nexus.misiones.configuracion;

import com.nexusbattles.comun.seguridad.CadenaDeSeguridad;
import com.nexusbattles.comun.seguridad.ConversorRolesJwt;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Seguridad del servicio de misiones: servidor de recursos contra el JWKS de
 * ms-identidad (ADR-002/ADR-005).
 *
 * <p>Todas las rutas son del jugador (misiones.yaml): un usuario real con su
 * {@code uid}, de cualquier rol de persona. Un token de servicio no sirve aqui
 * —ninguna ruta de este contrato es interna— y recibe 403. La identidad sale
 * siempre del token, nunca del cuerpo ni de la ruta.
 */
@Configuration
@EnableWebSecurity
public class SeguridadConfig {

    static final String[] ROLES_DE_PERSONA = {"JUGADOR", "MODERADOR", "ADMINISTRADOR", "SUPER_ADMINISTRADOR"};

    @Bean
    public ConversorRolesJwt conversorRolesJwt() {
        return new ConversorRolesJwt();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ConversorRolesJwt conversor) throws Exception {
        CadenaDeSeguridad.aplicarBase(http, conversor);

        // Regla 4: el mismo problem detail que el resto de modulos tambien en
        // el 401 y el 403, que la cadena base deja vacios.
        http.exceptionHandling(excepciones -> excepciones
                .authenticationEntryPoint((solicitud, respuesta, error) -> problema(solicitud, respuesta,
                        HttpStatus.UNAUTHORIZED, "No autenticado", "Se requiere un token Bearer valido",
                        "urn:nexus:problema:no-autenticado"))
                .accessDeniedHandler((solicitud, respuesta, error) -> problema(solicitud, respuesta,
                        HttpStatus.FORBIDDEN, "Acceso denegado", "Tu token no tiene permiso para esta operacion",
                        "urn:nexus:problema:acceso-denegado")));

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/api/v1/misiones", "/api/v1/misiones/**").hasAnyRole(ROLES_DE_PERSONA)
                .anyRequest().denyAll());
        return http.build();
    }

    static void problema(HttpServletRequest solicitud, HttpServletResponse respuesta, HttpStatus estado,
                         String titulo, String detalle, String tipo) throws IOException {
        respuesta.setStatus(estado.value());
        if (estado == HttpStatus.UNAUTHORIZED) {
            respuesta.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        respuesta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        respuesta.getWriter().write("""
                {"type":"%s","title":"%s","status":%d,"detail":"%s","instance":"%s"}"""
                .formatted(tipo, titulo, estado.value(), detalle, solicitud.getRequestURI()));
    }
}
