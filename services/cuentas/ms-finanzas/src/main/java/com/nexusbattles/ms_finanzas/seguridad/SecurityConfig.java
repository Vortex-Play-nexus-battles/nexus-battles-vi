package com.nexusbattles.ms_finanzas.seguridad;

import java.util.function.Supplier;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

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
 * <h2>El libro de créditos solo lo mueven los servicios (ADR-001 / ADR-005)</h2>
 *
 * <p>Hasta el 21-sep-2026 {@code /creditos/**} estaba {@code permitAll}
 * «hasta que infra registre el cliente m2m en Keycloak». ADR-005 resolvió
 * ese bloqueo sin Keycloak: {@code ms-identidad} emite credenciales de servicio
 * ({@code grant_type=client_credentials}, {@code rol=SERVICIO}, {@code azp} con
 * el {@code client_id}) firmadas con la misma clave que verifica este
 * servicio por {@code IDENTIDAD_JWKS_URL}. Con eso ya no hay motivo para dejar
 * abierta la puerta que crea, aparta y mueve saldo (#455, #413):
 *
 * <ul>
 *   <li>Reservar, liberar, consumir, debitar, reversar, acreditar y consultar
 *       operaciones: <b>solo</b> {@code ROLE_SERVICIO}. El jugador afectado
 *       viaja explícito en el cuerpo ({@code jugadorUid}, {@code uid},
 *       {@code vendedorUid}); el token solo dice qué servicio habla. Un jugador
 *       con su propio token recibe 403 aunque el {@code uid} sea el suyo: un
 *       jugador nunca se acredita ni se reserva a sí mismo por HTTP, eso lo
 *       hace el flujo de negocio (sala, subasta, tienda) con su credencial.</li>
 *   <li>{@code GET /creditos/{uid}/saldo}: un servicio consulta el de
 *       cualquiera; un usuario, <b>solo el suyo</b> (el {@code uid} de la ruta
 *       tiene que ser el principal del token, que {@link ConversorRolesJwt} fija
 *       en el claim {@code uid}). Consultar el saldo de otro es 403.</li>
 *   <li>{@code POST /partidas/resultado} (HU-JUE-012) crea saldo: solo
 *       {@code ROLE_SERVICIO}. Con {@code authenticated()} cualquier jugador
 *       podía inventar un resultado con su {@code uid} como ganador.</li>
 *   <li>{@code /transacciones/**} y {@code /cofres/**} son del propio usuario:
 *       autenticado y <b>no</b> servicio (un token de servicio no tiene
 *       {@code uid} y su principal sería el {@code client_id}).</li>
 * </ul>
 *
 * <p>Las pruebas de estas reglas están en {@code SecurityConfigTest} con tokens
 * reales firmados y verificados contra un JWKS (fixtures de
 * {@code plataforma-seguridad}), incluidos los casos de suplantación.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Rol con el que ms-identidad (y, en su día, Keycloak) marca a un servicio — ADR-005. */
    static final String ROL_SERVICIO = "SERVICIO";

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
                // El saldo: un servicio ve el de cualquiera; un usuario, solo el suyo.
                .requestMatchers(HttpMethod.GET, "/creditos/{uid}/saldo").access(servicioODuenoDelSaldo())
                // Todo lo que aparta, mueve o crea saldo: solo servicios autorizados.
                .requestMatchers("/creditos/**").hasRole(ROL_SERVICIO)
                // HU-JUE-012: el resultado de una partida lo informa salas-partidas,
                // nunca un jugador (crea saldo a favor del ganador).
                .requestMatchers("/partidas/**").hasRole(ROL_SERVICIO)
                // HU-PAG-002 / HU-JUE-013: historial y cofres del propio usuario. El
                // controller lee el uid del principal, así que un servicio (sin uid)
                // no tiene nada que consultar aquí.
                .requestMatchers("/transacciones/**", "/cofres/**").access(usuarioAutenticadoNoServicio())
                .anyRequest().authenticated());

        return http.build();
    }

    /**
     * {@code GET /creditos/{uid}/saldo}: pasa un servicio, o un usuario cuyo
     * principal (claim {@code uid}) coincide con el {@code uid} de la ruta.
     */
    static AuthorizationManager<RequestAuthorizationContext> servicioODuenoDelSaldo() {
        AuthorizationManager<RequestAuthorizationContext> esServicio =
                AuthorityAuthorizationManager.hasRole(ROL_SERVICIO);
        return (Supplier<? extends Authentication> autenticacion, RequestAuthorizationContext contexto) -> {
            AuthorizationResult servicio = esServicio.authorize(autenticacion, contexto);
            if (servicio != null && servicio.isGranted()) {
                return servicio;
            }
            Authentication actual = autenticacion.get();
            boolean autenticado = actual != null && actual.isAuthenticated()
                    && !(actual instanceof org.springframework.security.authentication.AnonymousAuthenticationToken);
            String uidDeLaRuta = contexto.getVariables().get("uid");
            boolean esElDueno = autenticado && uidDeLaRuta != null && uidDeLaRuta.equals(actual.getName());
            return new AuthorizationDecision(esElDueno);
        };
    }

    /** Autenticado y con un rol de usuario: un token de servicio no entra. */
    static AuthorizationManager<RequestAuthorizationContext> usuarioAutenticadoNoServicio() {
        AuthorizationManager<RequestAuthorizationContext> esServicio =
                AuthorityAuthorizationManager.hasRole(ROL_SERVICIO);
        return (Supplier<? extends Authentication> autenticacion, RequestAuthorizationContext contexto) -> {
            Authentication actual = autenticacion.get();
            boolean autenticado = actual != null && actual.isAuthenticated()
                    && !(actual instanceof org.springframework.security.authentication.AnonymousAuthenticationToken);
            AuthorizationResult servicio = esServicio.authorize(autenticacion, contexto);
            boolean esServicioAutorizado = servicio != null && servicio.isGranted();
            return new AuthorizationDecision(autenticado && !esServicioAutorizado);
        };
    }
}
