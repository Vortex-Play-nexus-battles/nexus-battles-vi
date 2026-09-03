package com.nexusbattles.plataforma.salaspartidas.seguridad;

import com.nexusbattles.comun.seguridad.CadenaDeSeguridad;
import com.nexusbattles.comun.seguridad.ConversorRolesJwt;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Seguridad del servicio de salas y partidas.
 *
 * <p>El andamiaje —sin CSRF, sin estado y token de Keycloak traducido— viene de
 * {@link CadenaDeSeguridad}, compartido con el resto de la plataforma. Aqui solo
 * las reglas de rutas de este dominio.
 *
 * <p>Crear una sala exige rol JUGADOR: RF-JUE-001 la describe como accion del
 * jugador, y la seccion 3.1.1 del SRS dice que el visitante «no podra participar
 * en partidas». Actuator queda abierto porque lo consulta la sonda de salud, que
 * no tiene token (regla 3).
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
                .requestMatchers("/api/v1/salas/**").hasRole("JUGADOR")
                .anyRequest().authenticated());

        return http.build();
    }
}
