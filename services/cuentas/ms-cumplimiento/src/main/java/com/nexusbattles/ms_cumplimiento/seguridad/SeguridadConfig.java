package com.nexusbattles.ms_cumplimiento.seguridad;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Seguridad de cumplimiento (HU-AUD-001, HU-RBAC-003).
 *
 * <p>Dos rutas, dos clases de llamador:
 * <ul>
 *   <li>{@code GET /api/v1/admin/auditoria}: la bitacora la consulta un
 *       {@code SUPER_ADMINISTRADOR} —el nombre real del rol que emite
 *       ms-identidad— con su token. {@code RequireSuperAdmin2FAAspect} anade
 *       la exigencia de segundo factor cuando este habilitada.</li>
 *   <li>{@code POST /api/v1/admin/auditoria/eventos}: lo escriben otros
 *       servicios (ms-identidad) con su credencial de servicio
 *       ({@code ROLE_SERVICIO}, ADR-001/ADR-005). Un usuario no escribe la
 *       bitacora: el {@code administradorId} del cuerpo es un dato del
 *       evento, no quien llama.</li>
 * </ul>
 *
 * <p>Actuator queda abierto para la sonda de salud (regla 3). Sin CSRF, sin
 * estado y JWT traducido con {@link ConversorDeRoles}: mismo andamiaje que
 * {@code CadenaDeSeguridad} de plataforma-seguridad, reproducido porque este
 * modulo sigue en Maven.
 */
@Configuration
@EnableWebSecurity
public class SeguridadConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // Sin CSRF: API sin estado con token portador, sin sesion ni cookies de
        // navegador que proteger (misma forma que CadenaDeSeguridad).
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(Customizer.withDefaults())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(new ConversorDeRoles())))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/auditoria/eventos").hasRole("SERVICIO")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/auditoria").hasRole("SUPER_ADMINISTRADOR")
                        .anyRequest().authenticated());
        return http.build();
    }
}
