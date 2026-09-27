package com.nexusbattles.ms_chatbot.config;

import com.nexusbattles.comun.seguridad.CadenaDeSeguridad;
import com.nexusbattles.comun.seguridad.ConversorRolesJwt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

// HU-CHA-001: el chat debe funcionar 24/7 para visitantes SIN autenticarse
// (criterio de aceptacion), asi que /chat/** queda en permitAll. Aun asi, si
// la peticion trae un JWT valido de ms-identidad, Spring Security lo valida
// e informa quien es -- eso es lo que usa ChatController.resolverIdentidad
// para distinguir visitante de usuario autenticado.
//
// HU-CHA-008: un JWT presente pero invalido/vencido no debe tumbar el chat
// con 401 -- debe degradar a modo visitante. Por eso /chat/** vive en su
// propia cadena de seguridad (orden 1), con JwtInvalidoComoVisitanteFilter
// delante del filtro de Resource Server. El resto de rutas usa la cadena
// estricta normal (orden 2), donde un JWT invalido si se rechaza con 401.
//
// HU-CHA-012: el panel de administracion (/chatbot/admin/**) va en la cadena
// estricta y exige rol ADMINISTRADOR o SUPER_ADMINISTRADOR. Los roles salen
// del claim "rol" del token de ms-identidad, traducido a ROLE_<rol> por
// ConversorRolesJwt (plataforma-seguridad, mismo patron que ms-finanzas).
//
// Por que /chatbot/admin y no /admin/chatbot: el borde (borde-dev.conf) envia
// todo /api/v1/admin... a ms-identidad; con ese prefijo, en el despliegue las
// peticiones del panel nunca llegarian a este servicio.
//
// CORS: solo importa en desarrollo local, cuando las vistas se sirven desde
// Live Server (5500) o `npm run dev` (8080) y llaman directo a este servicio
// (8094), que es otro origen. Detras del borde todo es mismo origen. Lista
// explicita de origenes (nunca "*"), igual que ms-subastas, configurable con
// la variable CORS_ORIGENES (regla 10).
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    static final String ROL_ADMINISTRADOR = "ADMINISTRADOR";
    static final String ROL_SUPER_ADMINISTRADOR = "SUPER_ADMINISTRADOR";

    @Bean
    public ConversorRolesJwt conversorRolesJwt() {
        return new ConversorRolesJwt();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
        @Value("${app.cors.origenes-permitidos}") List<String> origenesPermitidos) {

        CorsConfiguration configuracion = new CorsConfiguration();
        configuracion.setAllowedOrigins(origenesPermitidos);
        configuracion.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuracion.setAllowedHeaders(List.of(
            "Authorization", "Content-Type", "Accept", "X-Id-Sesion-Anonima", "traceparent"));
        // El panel descarga la exportacion (CSV y JSON) y necesita leer el
        // nombre del archivo; sin esto el navegador le oculta la cabecera.
        configuracion.setExposedHeaders(List.of("Content-Disposition"));
        configuracion.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/**", configuracion);
        return fuente;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain filterChainChat(HttpSecurity http, JwtDecoder jwtDecoder,
                                               ConversorRolesJwt conversor) throws Exception {
        http.securityMatcher("/chat/**");
        CadenaDeSeguridad.aplicarBase(http, conversor);
        http
            .cors(Customizer.withDefaults())
            .addFilterBefore(new JwtInvalidoComoVisitanteFilter(jwtDecoder), BearerTokenAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChainPorDefecto(HttpSecurity http, ConversorRolesJwt conversor) throws Exception {
        CadenaDeSeguridad.aplicarBase(http, conversor);
        http
            .cors(Customizer.withDefaults())
            .authorizeHttpRequests(auth -> auth
                // Regla 3 de plataforma: actuator abierto para la sonda de salud.
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/chatbot/admin/**").hasAnyRole(ROL_ADMINISTRADOR, ROL_SUPER_ADMINISTRADOR)
                .anyRequest().authenticated()
            );

        return http.build();
    }
}
