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
                // Andrés lo reportó el 18/sep — estaba abierto y con la
                // idempotencia efectiva rota (probado en vivo: dos llamadas
                // con el mismo refId sumaban dos veces), es decir cualquiera
                // con acceso al servicio podía regalarse créditos. Aun
                // después de que Juan Diego arreglara la idempotencia (que
                // ya está funcionando contra develop), sigue valiendo la
                // pena cerrar la ruta: idempotente o no, no debe aceptar
                // peticiones anónimas. Ms-subastas NO consume
                // /creditos/acreditar (verificado por Andrés — su
                // CreditoClientHttp solo usa reservar/liberar/consumir/saldo),
                // así que cerrar este endpoint concreto no rompe HU-SUB-001/004
                // y sí quita el agujero sin esperar al cliente m2m de
                // Keycloak. Este matcher va ANTES del permitAll genérico de
                // /creditos/** porque Spring toma el primero que coincide y
                // este es más específico. Mi propio AcreditacionPartidaService
                // consume acreditar como bean local en el mismo módulo Java,
                // no por HTTP, así que la ruta cerrada no lo afecta.
                .requestMatchers(HttpMethod.POST, "/creditos/acreditar").authenticated()
                // Temporal (ver javadoc): el resto de /creditos/** sigue
                // abierto hasta que infra registre el cliente m2m en Keycloak
                // y ms-subastas adopte TokenDeServicio (ADR-001). Los tres
                // flujos @Scheduled de ms-subastas no tienen JWT de jugador
                // que reenviar.
                .requestMatchers("/creditos/**").permitAll()
                // POST /partidas/resultado CREA saldo (acredita créditos al
                // ganador y participantes vía AcreditacionPartidaService que
                // llama a CreditoService.acreditar como bean local — se salta
                // el filter chain de /creditos/acreditar). Sin autenticación
                // sería exactamente el mismo agujero por otra puerta:
                // cualquiera POST-ea un resultado inventado con su uid como
                // ganador y se autoacredita. Cerrado con authenticated tras
                // el catch de Andrés (18/sep). Consecuencia: ms-salas-partidas
                // necesita el token de servicio de Keycloak para llamar,
                // igual que ms-subastas ahora sabe con /creditos/**. Sin
                // eso, en dev/local se prueba con un JWT de jugador válido.
                .requestMatchers("/partidas/**").authenticated()
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
