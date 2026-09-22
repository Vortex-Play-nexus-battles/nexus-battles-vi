package com.nexusbattles.plataforma.metricasplataforma.seguridad;

import com.nexusbattles.comun.seguridad.CadenaDeSeguridad;
import com.nexusbattles.comun.seguridad.ConversorRolesJwt;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Observabilidad de la plataforma: quien la consulta es administracion.
 *
 * <p>La ficha de HU-MET-001 (RF-MET-001) dice «Como administrador», y el resto
 * de lo que publica este modulo —tablero tecnico (HU-MET-004), disponibilidad
 * (HU-DIS-001), latencia y consultas (HU-REN-001/003) y degradacion
 * (HU-DIS-003)— describe el estado interno del bloque: cuanta memoria gasta
 * cada servicio, cuantos 5xx lleva, cuando se cayo y cuantas sanciones se
 * emitieron. Nada de eso es informacion de jugador.
 *
 * <p>Hasta el 22-sep-2026 este servicio no tenia ninguna cadena de seguridad y
 * todo eso respondia sin token a cualquiera que llegara por el borde. La
 * cabecera de la aplicacion ya reservaba el «Panel de observabilidad» a los
 * roles administrativos distintos de MODERADOR; esta cadena hace que esa
 * decision valga tambien cuando se llama a la API directamente.
 *
 * <p>{@code SERVICIO} entra porque un modulo puede consultar la degradacion del
 * bloque con su credencial (ADR-005) sin que haya una persona detras.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Los que ve el panel de observabilidad en `cabecera-app.js` (sin MODERADOR). */
    static final String[] ROLES_DE_OBSERVABILIDAD = {"ADMINISTRADOR", "SUPER_ADMINISTRADOR", "SERVICIO"};

    @Bean
    public ConversorRolesJwt conversorRolesJwt() {
        return new ConversorRolesJwt();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ConversorRolesJwt conversor) throws Exception {
        CadenaDeSeguridad.aplicarBase(http, conversor);
        http.authorizeHttpRequests(auth -> auth
                // Regla 3 de plataforma: la sonda de salud y las metricas de
                // Actuator las lee el propio monitor de este modulo y el
                // despliegue, sin token.
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .anyRequest().hasAnyRole(ROLES_DE_OBSERVABILIDAD));
        return http.build();
    }
}
