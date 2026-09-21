package com.nexusbattles.ms_ecommerce.seguridad;

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
 * Seguridad de la tienda (HU-ECO-*).
 *
 * <p>Hasta ahora el servicio no tenia cadena de seguridad y el carrito era
 * del valor que trajera {@code X-User-Id}: cualquiera podia ver y modificar el
 * carrito de otro escribiendo su identificador. Ahora es servidor de recursos
 * (ADR-002): el carrito es del {@code uid} del token, verificado contra el
 * JWKS de ms-identidad.
 *
 * <p>La vitrina ({@code GET /api/v1/productos}) sigue publica: es el catalogo
 * que ve cualquiera que entre a la tienda. Actuator queda abierto para la
 * sonda de salud (regla 3). Las rutas son relativas al context-path
 * {@code /ecommerce} del servicio.
 *
 * <p>Mismo andamiaje que {@code CadenaDeSeguridad} de plataforma-seguridad
 * (sin CSRF, sin estado, JWT traducido con {@link ConversorDeRoles}); se
 * reproduce aqui porque este modulo sigue en Maven y no enlaza la biblioteca
 * Gradle. Cuando migre, esta clase se reduce a las reglas de rutas.
 */
@Configuration
@EnableWebSecurity
public class SeguridadConfig {

    static final String[] ROLES_DE_USUARIO = {"JUGADOR", "MODERADOR", "ADMINISTRADOR", "SUPER_ADMINISTRADOR"};

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
                        .requestMatchers(HttpMethod.GET, "/api/v1/productos", "/api/v1/productos/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/v1/carrito/**").hasAnyRole(ROLES_DE_USUARIO)
                        .anyRequest().authenticated());
        return http.build();
    }
}
