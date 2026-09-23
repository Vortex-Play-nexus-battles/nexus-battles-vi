package com.nexusbattles.ms_chatbot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

// HU-CHA-001: el chat debe funcionar 24/7 para visitantes SIN autenticarse
// (criterio de aceptacion), asi que /chat/** queda en permitAll. Aun asi, si
// la peticion trae un JWT valido de ms-identidad, Spring Security lo valida
// e informa quien es -- eso es lo que usa ChatController.resolverIdentidad
// para distinguir visitante de usuario autenticado.
//
// HU-CHA-008: un JWT presente pero invalido/vencido no debe tumbar el chat
// con 401 -- debe degradar a modo visitante. Por eso /chat/** vive en su
// propia cadena de seguridad (orden 1), con JwtInvalidoComoVisitanteFilter
// delante del filtro de Resource Server. El resto de rutas (por ejemplo las
// administrativas de HU-CHA-012, pendiente) usa la cadena estricta normal
// (orden 2), donde un JWT invalido si se rechaza con 401.
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain filterChainChat(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        http
            .securityMatcher("/chat/**")
            .csrf(AbstractHttpConfigurer::disable)
            .addFilterBefore(new JwtInvalidoComoVisitanteFilter(jwtDecoder), BearerTokenAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {
            }));

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChainPorDefecto(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {
            }));

        return http.build();
    }
}
