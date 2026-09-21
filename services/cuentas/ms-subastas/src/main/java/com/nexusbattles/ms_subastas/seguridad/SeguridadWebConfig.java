package com.nexusbattles.ms_subastas.seguridad;

import com.nexusbattles.comun.seguridad.CadenaDeSeguridad;
import com.nexusbattles.comun.seguridad.ConversorRolesJwt;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cadena de seguridad de ms-subastas — ADR-002, mismo servidor de recursos
 * que el resto de servicios (JWKS de ms-identidad, roles y {@code uid} por
 * {@link ConversorRolesJwt}).
 *
 * <p>Hasta el 21-sep este servicio validaba el token con una clave HS256
 * propia ({@code app.jwt.clave-secreta}) que ms-identidad nunca uso: firma
 * RS256 y publica su clave publica en {@code /api/v1/auth/jwks}. Resultado:
 * todo inicio de sesion real acababa en 401 aqui, y la cadena de Spring
 * Security quedo en {@code permitAll} de forma transitoria (#468). Esta
 * configuracion cierra las dos cosas a la vez.
 *
 * <p><b>Que es publico:</b> el listado y las sugerencias (RF-SUB-011: cualquiera
 * puede mirar que se subasta), el historial de pujas de una subasta (no
 * devuelve apodos) y el apreton de manos del contador en vivo
 * ({@code /ws-subastas}). <b>Todo lo demas</b> —publicar, pujar, comprar ya,
 * puja automatica, mis pujas, mi participacion— exige un usuario autenticado;
 * quien es sale del token ({@link IdentidadDesdeToken}), nunca del cuerpo.
 *
 * <p>Las rutas van sin {@code /api/v1}: es el context-path del servicio.
 */
@Configuration
@EnableWebSecurity
public class SeguridadWebConfig {

    static final String[] ROLES_DE_USUARIO = {"JUGADOR", "MODERADOR", "ADMINISTRADOR", "SUPER_ADMINISTRADOR"};

    @Bean
    public ConversorRolesJwt conversorRolesJwt() {
        return new ConversorRolesJwt();
    }

    @Bean
    public SecurityFilterChain cadenaDeSubastas(HttpSecurity http, ConversorRolesJwt conversor) throws Exception {
        CadenaDeSeguridad.aplicarBase(http, conversor);
        http.authorizeHttpRequests(auth -> auth
                // El preflight CORS no lleva credenciales: lo contesta la
                // configuracion CORS de MVC (ConfiguracionCors), no la cadena.
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/ws-subastas/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/subastas", "/subastas/sugerencias").permitAll()
                .requestMatchers(HttpMethod.GET, "/subastas/*/pujas").permitAll()
                .anyRequest().hasAnyRole(ROLES_DE_USUARIO));
        return http.build();
    }
}
