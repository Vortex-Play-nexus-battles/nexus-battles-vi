package com.nexusbattles.plataforma.comentarios.seguridad;

import com.nexusbattles.comun.seguridad.CadenaDeSeguridad;
import com.nexusbattles.comun.seguridad.ConversorRolesJwt;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Seguridad del servicio de comentarios (HU-COM-001).
 *
 * <p>El andamiaje —sin CSRF, sin estado y token traducido a autenticacion—
 * viene de {@link CadenaDeSeguridad}, compartido con el resto de la
 * plataforma (ADR-002). Aqui solo las reglas de rutas de este dominio.
 *
 * <p><b>Leer el hilo es publico.</b> La ficha de producto (HU-INV-014) lo
 * pinta para cualquiera que mire el catalogo, y no revela nada que no sea
 * publico ya: los comentarios retenidos no salen.
 *
 * <p><b>Publicar exige un usuario autenticado.</b> El autor y su apodo salen
 * del token (ADR-002: {@code uid} estable y apodo), no del cuerpo: antes
 * cualquiera podia firmar un comentario como otra persona escribiendo su
 * {@code autorId}. Los cuatro roles de usuario pueden comentar; un token de
 * servicio (ADR-005) no, porque no identifica a nadie que pueda ser autor.
 *
 * <p>Actuator queda abierto para la sonda de salud (regla 3).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Los que pueden ser autores: personas, no servicios. */
    static final String[] ROLES_DE_USUARIO = {"JUGADOR", "MODERADOR", "ADMINISTRADOR", "SUPER_ADMINISTRADOR"};

    /**
     * Los que pueden moderar — R10.1.
     *
     * <p>Un JUGADOR reporta pero no resuelve: eso es justo la separacion que
     * hace que reportar sirva para algo. Si quien marca pudiera tambien
     * decidir, el reporte no seria una peticion de revision sino una orden.
     */
    static final String[] ROLES_DE_MODERACION = {"MODERADOR", "ADMINISTRADOR", "SUPER_ADMINISTRADOR"};

    @Bean
    public ConversorRolesJwt conversorRolesJwt() {
        return new ConversorRolesJwt();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ConversorRolesJwt conversor) throws Exception {
        CadenaDeSeguridad.aplicarBase(http, conversor);

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/products/*/comments").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/products/*/comments").hasAnyRole(ROLES_DE_USUARIO)
                .requestMatchers(HttpMethod.DELETE, "/api/v1/products/*/comments/*").hasAnyRole(ROLES_DE_USUARIO)
                // R10.1 — reportar lo hace cualquier persona autenticada
                // (RF-COM-006); resolver, solo quien modera (RF-COM-008). El
                // orden importa: esta linea va ANTES que /moderacion/** porque
                // vive bajo otro prefijo, pero se deja junta a proposito para
                // que las dos mitades del flujo se lean de un vistazo.
                .requestMatchers(HttpMethod.POST, "/api/v1/products/*/comments/*/reportes")
                    .hasAnyRole(ROLES_DE_USUARIO)
                .requestMatchers("/api/v1/moderacion/**").hasAnyRole(ROLES_DE_MODERACION)
                .anyRequest().authenticated());

        return http.build();
    }
}
