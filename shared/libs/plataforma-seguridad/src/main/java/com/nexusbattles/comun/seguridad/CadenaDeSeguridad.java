package com.nexusbattles.comun.seguridad;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;

/**
 * Andamiaje comun de la cadena de filtros de todo servicio de la plataforma.
 *
 * <p>Aplica lo que en los 20 modulos es identico y no se discute: sin CSRF
 * (no hay sesion de navegador que proteger), sin estado en servidor, y el token
 * de Keycloak traducido por {@link ConversorRolesJwt}.
 *
 * <p><b>Lo que NO hace, a proposito:</b> declarar reglas de rutas. Que camino es
 * publico y que rol hace falta es una decision del dominio de cada servicio, y
 * esconderla aqui la volveria invisible en la revision de codigo. Cada servicio
 * llama a {@link #aplicarBase} y despues declara sus propias reglas.
 *
 * <p>Uso tipico:
 * <pre>{@code
 * @Bean
 * SecurityFilterChain filterChain(HttpSecurity http, ConversorRolesJwt conversor) throws Exception {
 *     CadenaDeSeguridad.aplicarBase(http, conversor);
 *     http.authorizeHttpRequests(auth -> auth
 *             .requestMatchers("/actuator/**").permitAll()
 *             .requestMatchers("/api/v1/mi-recurso/**").hasRole("JUGADOR")
 *             .anyRequest().authenticated());
 *     return http.build();
 * }
 * }</pre>
 */
public final class CadenaDeSeguridad {

    private CadenaDeSeguridad() {
        // Utilidad: no se instancia.
    }

    /**
     * Deja {@code http} configurado como servidor de recursos sin estado.
     *
     * @param http      cadena en construccion
     * @param conversor traductor de los roles de Keycloak
     */
    public static void aplicarBase(HttpSecurity http, ConversorRolesJwt conversor) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(conversor)));
    }
}
