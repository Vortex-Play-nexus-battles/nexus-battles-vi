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
        // CSRF desactivado, sin estado y JWT de Keycloak traducido: todo eso lo
        // pone CadenaDeSeguridad, compartida con el resto de la plataforma.
        CadenaDeSeguridad.aplicarBase(http, conversor);

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/api/v1/lista-negra/verificar").permitAll()
                // Consulta de sancion activa: la llaman otros servicios antes de
                // dejar actuar a un jugador, igual que la verificacion de
                // terminos. Regla traida de develop al integrar.
                .requestMatchers("/api/v1/sanciones/usuarios/*/activa").permitAll()
                // Agregados de moderacion (HU-MET-001): solo cuentas, sin
                // identificadores; los lee metricas-plataforma, que no lleva
                // credencial de servicio.
                .requestMatchers("/api/v1/sanciones/metricas").permitAll()
                .requestMatchers("/api/v1/lista-negra/terminos/**")
                .hasAnyRole("ADMINISTRADOR", "MODERADOR")
                // Sanciones y apelaciones (HU-USR-004..007): cualquier usuario
                // autenticado entra —el propio sancionado ve su historial y
                // apela—; quien puede emitir, resolver o ver a otros lo decide
                // SancionesService por el rol del token (moderador solo
                // temporal, baneo de administrador). Un token de servicio no
                // sanciona a nadie.
                .requestMatchers("/api/v1/sanciones/**", "/api/v1/apelaciones/**")
                .hasAnyRole("JUGADOR", "MODERADOR", "ADMINISTRADOR", "SUPER_ADMINISTRADOR")
                .anyRequest().authenticated());

        return http.build();
    }
}
