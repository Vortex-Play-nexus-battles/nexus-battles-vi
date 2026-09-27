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
 *   <li>Cualquier otra ruta bajo {@code /api/v1/admin/auditoria}, con
 *       cualquier metodo: solo {@code SUPER_ADMINISTRADOR} (RF-AUD-003). Una
 *       ruta nueva, como la exportacion de HU-AUD-004, nace cerrada y no
 *       depende de que alguien recuerde {@code @RequireSuperAdmin2FA} para no
 *       quedar abierta a cualquier autenticado. Si alguna tiene que admitir
 *       otro rol, se declara expresamente ANTES de esa regla.</li>
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

    /**
     * S4502 (CSRF desactivado) revisado y aceptado: esta API no tiene sesion
     * ni cookies de navegador — es sin estado y solo acepta un token portador
     * en la cabecera, que un sitio ajeno no puede adjuntar — asi que no hay
     * peticion entre sitios que falsificar. Es la misma decision, con la
     * misma justificacion, que {@code CadenaDeSeguridad} en
     * shared/libs/plataforma-seguridad para los 20 modulos.
     */
    @Bean
    @SuppressWarnings("java:S4502")
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(Customizer.withDefaults())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(new ConversorDeRoles())))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/auditoria/eventos").hasRole("SERVICIO")
                        // El resto de la bitacora nace cerrado: la consulta y cualquier
                        // ruta nueva (la exportacion de HU-AUD-004, por ejemplo).
                    .requestMatchers("/api/v1/admin/auditoria", "/api/v1/admin/auditoria/**")
                    .hasRole("SUPER_ADMINISTRADOR")
                        .anyRequest().authenticated());
        return http.build();
    }
}
