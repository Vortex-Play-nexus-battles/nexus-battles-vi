package com.nexusbattles.plataforma.torneos.seguridad;

import com.nexusbattles.comun.seguridad.CadenaDeSeguridad;
import com.nexusbattles.comun.seguridad.ConversorRolesJwt;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Consultar torneos es publico (RF-TOR-008: el espectador ve equipos y arbol).
 * Todo lo demas exige token: quien puede que cosa lo decide TorneosService
 * por el rol (administrador crea/inicia/cancela, jugador registra equipo e
 * inscribe, servicio o administrador con motivo registra resultados).
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
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/torneos/**").permitAll()
                .requestMatchers("/api/v1/torneos/*/encuentros/*/resultado")
                .hasAnyRole("SERVICIO", "ADMINISTRADOR", "SUPER_ADMINISTRADOR")
                .requestMatchers("/api/v1/torneos/**")
                .hasAnyRole("JUGADOR", "MODERADOR", "ADMINISTRADOR", "SUPER_ADMINISTRADOR")
                .anyRequest().authenticated());
        return http.build();
    }
}
