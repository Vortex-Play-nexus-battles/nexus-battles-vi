package com.nexusbattles.ms_finanzas.seguridad;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

import com.nexusbattles.comun.seguridad.CadenaDeSeguridad;
import com.nexusbattles.comun.seguridad.ConversorRolesJwt;

/**
 * Seguridad del servicio de finanzas.
 *
 * <p>El andamiaje —sin CSRF, sin estado y token traducido a Authentication—
 * viene de {@link CadenaDeSeguridad}, compartido con el resto de la plataforma
 * (adoptado desde el PR #393, ADR-002). Aquí solo las reglas de rutas de este
 * dominio.
 *
 * <h2>Por qué /creditos/** queda abierto (por ahora)</h2>
 *
 * <p>Andrés (ms-subastas) tiene tres flujos que llaman a {@code /creditos/**}
 * sin JWT de jugador de por medio (dos {@code @Scheduled} y el flujo de
 * liberar la reserva del postor anterior al ser superado). No puede reenviar
 * el token del rival porque sería usar el token de un jugador para mover
 * créditos de otro. La solución acordada es que ms-subastas envíe un token de
 * servicio (client_credentials contra el realm de Keycloak) usando
 * {@code shared/libs/plataforma-seguridad/servicio/TokenDeServicio}
 * (ADR-001), pero **infra todavía no ha registrado el cliente en el realm**.
 * Hasta que exista ese registro, dejar {@code /creditos/**} exigiendo
 * autenticación cerraría a Andrés y bloquearía HU-SUB-001/004.
 *
 * <p>Cuando infra registre el client, se cambia
 * {@code .requestMatchers("/creditos/**").permitAll()} por
 * {@code .authenticated()} (o por un {@code hasRole} concreto de servicio),
 * y en paralelo se agrega el segundo issuer (Keycloak) al
 * {@code oauth2ResourceServer} para aceptar tanto tokens de ms-identidad
 * como de Keycloak.
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
                // Regla 3: actuator queda abierto para la sonda de salud.
                .requestMatchers("/actuator/**").permitAll()
                // Temporal (ver javadoc): /creditos/** y /partidas/** abiertos
                // hasta que infra registre el cliente m2m en Keycloak y los
                // servicios llamantes (ms-subastas para créditos, ms-salas-partidas
                // para partidas/resultado) adopten TokenDeServicio (ADR-001).
                .requestMatchers("/creditos/**").permitAll()
                .requestMatchers("/partidas/**").permitAll()
                // HU-PAG-002: el historial es del propio jugador; cualquier
                // usuario autenticado puede consultar SU propio historial. La
                // restricción por uid la aplica el controller leyendo el
                // principal del Authentication (nunca del path/query).
                .requestMatchers("/transacciones/**").authenticated()
                // HU-JUE-012: "Mis cofres" — mismo criterio que el historial.
                .requestMatchers("/cofres/**").authenticated()
                .anyRequest().authenticated());

        return http.build();
    }
}
