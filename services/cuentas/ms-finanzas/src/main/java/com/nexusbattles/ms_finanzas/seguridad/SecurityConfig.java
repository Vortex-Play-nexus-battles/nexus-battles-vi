package com.nexusbattles.ms_finanzas.seguridad;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
                // Excepción específica: POST /creditos/acreditar es el único
                // endpoint del módulo créditos que CREA saldo "de la nada".
                // Andrés reportó el 18/sep que estaba abierto y con
                // idempotencia efectiva rota (probado en vivo: dos
                // llamadas con el mismo refId sumaban dos veces), o sea que
                // cualquiera con acceso al servicio podía regalarse créditos.
                // Ms-subastas NO consume /creditos/acreditar (verificado por
                // Andrés — su CreditoClientHttp solo usa reservar/liberar/
                // consumir/saldo), así que cerrar este endpoint concreto no
                // rompe HU-SUB-001/004 y sí quita el agujero grande sin
                // esperar al cliente m2m de Keycloak. Este matcher va ANTES
                // del permitAll genérico de /creditos/** porque Spring toma
                // el primero que coincide y este es más específico.
                //
                // El bug de idempotencia sigue siendo responsabilidad de
                // Juan Diego (CreditoService.acreditar) y se le pasó por
                // separado; con la ruta cerrada por auth, al menos deja de
                // ser explotable desde fuera aunque el bug siga latente.
                .requestMatchers(HttpMethod.POST, "/creditos/acreditar").authenticated()
                // Temporal (ver javadoc): el resto de /creditos/** sigue
                // abierto hasta que infra registre el cliente m2m en Keycloak
                // y ms-subastas adopte TokenDeServicio.
                .requestMatchers("/creditos/**").permitAll()
                // HU-PAG-002: el historial es del propio jugador; cualquier
                // usuario autenticado puede consultar SU propio historial. La
                // restricción por uid la aplica el controller leyendo el
                // principal del Authentication (nunca del path/query).
                .requestMatchers("/transacciones/**").authenticated()
                .anyRequest().authenticated());

        return http.build();
    }
}
