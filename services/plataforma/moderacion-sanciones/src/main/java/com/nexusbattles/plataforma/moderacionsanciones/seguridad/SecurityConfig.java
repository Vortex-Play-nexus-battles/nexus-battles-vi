package com.nexusbattles.plataforma.moderacionsanciones.seguridad;

import com.nexusbattles.comun.seguridad.CadenaDeSeguridad;
import com.nexusbattles.comun.seguridad.ConversorRolesJwt;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Seguridad del servicio de moderacion y sanciones.
 *
 * <p>El andamiaje —sin CSRF, sin estado y token de {@code ms-identidad}
 * traducido— viene de {@link CadenaDeSeguridad}, compartido con el resto de la
 * plataforma. Aqui quedan las reglas de rutas de este dominio:
 *
 * <ul>
 *   <li>{@code POST /lista-negra/verificar}: publica (el registro avisa del
 *       apodo antes de enviarlo), pero el detalle solo lo ve un servicio o
 *       quien modera ({@link JerarquiaDeRoles#puedeVerDetalleDeListaNegra}).
 *       Moderacion-lista-negra.yaml 2.0.x.</li>
 *   <li>{@code /lista-negra/terminos}: MODERADOR o superior. Con la
 *       {@link JerarquiaDeRoles jerarquia} de la Tabla 24 un Super
 *       Administrador ya no recibe 403.</li>
 *   <li>{@code GET /sanciones/usuarios/{uid}/activa}: la llaman otros
 *       servicios antes de dejar actuar a un jugador.</li>
 *   <li>{@code GET /sanciones/metricas}: publica, solo cuentas sin
 *       identificadores (HU-MET-001).</li>
 *   <li>el resto de {@code /sanciones} y {@code /apelaciones}: cualquier
 *       usuario autenticado; quien puede emitir, resolver, levantar o ver a
 *       otros lo decide {@code SancionesService} por el rol. Un token de
 *       servicio no sanciona a nadie.</li>
 * </ul>
 *
 * <p>El 401 y el 403 de la cadena salen como problem details (regla 4), con el
 * mismo {@code type} que los del servicio; antes eran el cuerpo vacio de
 * Spring.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    static final String BASE_DE_ERRORES = "https://nexusbattles.local/errores/";

    @Bean
    public ConversorRolesJwt conversorRolesJwt() {
        return new ConversorRolesJwt();
    }

    /**
     * La cadena ({@code authorizeHttpRequests}) usa el bean de jerarquia que
     * encuentre; es el mismo objeto que consulta {@link JerarquiaDeRoles}.
     */
    @Bean
    public RoleHierarchy jerarquiaDeRoles() {
        return JerarquiaDeRoles.JERARQUIA;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ConversorRolesJwt conversor) throws Exception {
        // CSRF desactivado, sin estado y JWT de ms-identidad traducido (el
        // emisor real, ADR-005; Keycloak nunca se aprovisiono): todo eso lo
        // pone CadenaDeSeguridad, compartida con el resto de la plataforma.
        CadenaDeSeguridad.aplicarBase(http, conversor);

        http.exceptionHandling(excepciones -> excepciones
                .authenticationEntryPoint((solicitud, respuesta, error) -> problema(solicitud, respuesta,
                        HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO", "No autenticado",
                        "Hace falta un token Bearer valido de ms-identidad"))
                .accessDeniedHandler((solicitud, respuesta, error) -> problema(solicitud, respuesta,
                        HttpStatus.FORBIDDEN, "PERMISO_INSUFICIENTE", "No tienes permiso para esto",
                        "Tu rol no permite esta operacion")));
        // El 401 de un token presente pero invalido lo decide el filtro del
        // servidor de recursos, no el punto de entrada general: se le da el
        // mismo cuerpo.
        http.oauth2ResourceServer(oauth2 -> oauth2.authenticationEntryPoint((solicitud, respuesta, error) ->
                problema(solicitud, respuesta, HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO", "No autenticado",
                        "El token no es valido o ha caducado")));

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/lista-negra/verificar").permitAll()
                .requestMatchers("/api/v1/lista-negra/terminos/**").hasRole(JerarquiaDeRoles.MODERADOR)
                .requestMatchers("/api/v1/sanciones/usuarios/*/activa").permitAll()
                // Agregados de moderacion (HU-MET-001): solo cuentas, sin
                // identificadores; los lee metricas-plataforma, que no lleva
                // credencial de servicio.
                .requestMatchers(HttpMethod.GET, "/api/v1/sanciones/metricas").permitAll()
                .requestMatchers("/api/v1/sanciones/**", "/api/v1/apelaciones/**")
                .hasAnyRole(JerarquiaDeRoles.JUGADOR, JerarquiaDeRoles.MODERADOR)
                .anyRequest().authenticated());

        return http.build();
    }

    /** Problem details escrito a mano: aqui aun no hay controlador ni conversor de mensajes. */
    static void problema(HttpServletRequest solicitud, HttpServletResponse respuesta, HttpStatus estado,
                         String motivo, String titulo, String detalle) throws IOException {
        respuesta.setStatus(estado.value());
        if (estado == HttpStatus.UNAUTHORIZED) {
            respuesta.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        respuesta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        String tipo = BASE_DE_ERRORES + motivo.toLowerCase().replace('_', '-');
        respuesta.getWriter().write("{\"type\":\"" + tipo + "\",\"title\":\"" + titulo + "\",\"status\":"
                + estado.value() + ",\"detail\":\"" + detalle + "\",\"instance\":\""
                + json(solicitud.getRequestURI()) + "\",\"motivo\":\"" + motivo + "\"}");
    }

    /** Lo unico variable del cuerpo es la ruta; se escapa por si trae comillas o barras. */
    static String json(String texto) {
        if (texto == null) {
            return "";
        }
        StringBuilder salida = new StringBuilder(texto.length());
        for (char c : texto.toCharArray()) {
            if (c == '"' || c == '\\') {
                salida.append('\\').append(c);
            } else if (c < 0x20) {
                salida.append(String.format("\\u%04x", (int) c));
            } else {
                salida.append(c);
            }
        }
        return salida.toString();
    }
}
