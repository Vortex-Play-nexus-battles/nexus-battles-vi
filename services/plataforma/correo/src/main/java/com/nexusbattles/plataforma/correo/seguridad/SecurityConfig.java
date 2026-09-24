package com.nexusbattles.plataforma.correo.seguridad;

import com.nexusbattles.comun.seguridad.CadenaDeSeguridad;
import com.nexusbattles.comun.seguridad.ConversorRolesJwt;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Seguridad del servicio de correo (HU-COR-001/002/003/005).
 *
 * <p>El andamiaje —sin CSRF, sin estado y token traducido a autenticacion—
 * viene de {@link CadenaDeSeguridad}, compartido con el resto de la
 * plataforma (ADR-002). Aqui solo las reglas de rutas de este dominio.
 *
 * <p><b>Correo es un servicio entre servicios.</b> Ningun navegador lo llama:
 * quien pide un correo es ms-identidad (bienvenida, aviso de acceso,
 * confirmacion de cuenta, recuperacion de clave) y, en Sprint 2, misiones y
 * subastas. Por eso todas sus rutas exigen una credencial de servicio
 * ({@code ROLE_SERVICIO}, ADR-001/ADR-005) y ningun token de usuario vale:
 * hasta ahora cualquiera que alcanzara el puerto podia enviar a cualquier
 * direccion un correo con la plantilla corporativa, incluido un codigo de
 * recuperacion de contrasena o de confirmacion de cuenta.
 *
 * <p>Actuator queda abierto para la sonda de salud (regla 3).
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
                // La evidencia de entrega (RF-COR-001) la consulta una persona
                // desde la consola, no un servicio. Es de solo lectura, no
                // devuelve direcciones completas ni el cuerpo de ningun
                // mensaje, y es lo que permite responder "salio o no salio"
                // sin abrir la bitacora del contenedor.
                .requestMatchers(HttpMethod.GET, "/api/v1/correos/envios")
                .hasAnyRole("SERVICIO", "ADMINISTRADOR", "SUPER_ADMINISTRADOR")
                // Enviar sigue siendo solo entre servicios: quien alcance el
                // puerto no puede mandar a cualquier direccion un codigo de
                // recuperacion con la plantilla corporativa.
                .requestMatchers("/api/v1/correos/**").hasRole("SERVICIO")
                .anyRequest().authenticated());

        return http.build();
    }
}
