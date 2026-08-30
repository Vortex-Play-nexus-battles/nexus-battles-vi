package com.nexusbattles.plataforma.moderacionsanciones.seguridad;

import com.nexusbattles.comun.seguridad.CadenaDeSeguridad;
import com.nexusbattles.comun.seguridad.ConversorRolesJwt;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Seguridad del servicio de moderacion y sanciones.
 *
 * <p>El andamiaje —sin CSRF, sin estado y token de Keycloak traducido— viene de
 * {@link CadenaDeSeguridad}, compartido con el resto de la plataforma. Aqui solo
 * quedan las reglas de rutas de este dominio, que son las mismas de antes:
 * CA-03 restringe el panel de lista negra a ADMINISTRADOR y MODERADOR, mientras
 * que la verificacion de terminos queda abierta porque la consultan otros
 * servicios en cada mensaje.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public ConversorRolesJwt conversorRolesJwt() {
        return new ConversorRolesJwt();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ConversorRolesJwt conversor) throws Exception {
        CadenaDeSeguridad.aplicarBase(http, conversor);

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/api/v1/lista-negra/verificar").permitAll()
                .requestMatchers("/api/v1/lista-negra/terminos/**")
                .hasAnyRole("ADMINISTRADOR", "MODERADOR")
                .anyRequest().authenticated());

        return http.build();
    }
}
