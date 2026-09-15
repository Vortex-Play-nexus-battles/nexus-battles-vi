package com.nexusbattles.comun.seguridad.servicio.it;

import com.nexusbattles.comun.seguridad.CadenaDeSeguridad;
import com.nexusbattles.comun.seguridad.ConversorRolesJwt;
import com.nexusbattles.comun.seguridad.servicio.ActorDeServicio;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Servidor de recursos de juguete para probar el patron de ADR-001 de punta a
 * punta. Hace el papel de «inventario»: expone una operacion interna que solo
 * puede invocar un servicio autorizado, y opera sobre el jugador que viene en
 * el cuerpo, nunca sobre el que «firma» el token.
 *
 * <p>Es codigo de prueba: ni inventario ni subastas se tocan aqui. El nombre
 * del rol es PROVISIONAL (ver realm-de-prueba.json).
 */
@SpringBootApplication(scanBasePackageClasses = AplicacionDePrueba.class)
public class AplicacionDePrueba {

    /** Nombre provisional del rol de servicio; el definitivo lo fijan los tres SM. */
    static final String ROL_DE_SERVICIO_PROVISIONAL = "SERVICIO_SUBASTAS";

    @Configuration
    @EnableWebSecurity
    static class SeguridadDePrueba {

        @Bean
        ConversorRolesJwt conversorRolesJwt() {
            return new ConversorRolesJwt();
        }

        @Bean
        SecurityFilterChain cadena(HttpSecurity http, ConversorRolesJwt conversor) throws Exception {
            // Lo mismo que hace cualquier servicio de la plataforma: andamiaje
            // comun + sus propias reglas. Aqui, una sola ruta interna.
            CadenaDeSeguridad.aplicarBase(http, conversor);
            http.authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/v1/prueba/elementos/*/bloqueo-subasta")
                    .hasRole(ROL_DE_SERVICIO_PROVISIONAL)
                    // Si el controlador fallara, Boot reenvia a /error; con
                    // denyAll ahi, cualquier 500 se disfrazaria de 403.
                    .requestMatchers("/error").permitAll()
                    .anyRequest().denyAll());
            return http.build();
        }
    }

    /** Cuerpo de la operacion: el propietario afectado es DATO DE NEGOCIO. */
    public record Bloqueo(String subastaId, String propietarioUid) {
    }

    @RestController
    public static class ControladorDePrueba {

        @PutMapping("/api/v1/prueba/elementos/{elementoId}/bloqueo-subasta")
        // Nombre explicito: esta biblioteca no se compila con -parameters (no lleva
        // el plugin de Boot), asi que Spring no puede deducirlo por reflexion.
        public Map<String, String> bloquear(@PathVariable("elementoId") String elementoId,
                                            @RequestBody Bloqueo bloqueo,
                                            Authentication autenticacion) {
            // Separacion que exige ADR-001: el actor sale del token (azp); el
            // propietario sale del cuerpo. El servidor devuelve ambos para que
            // la prueba compruebe que no se mezclan.
            return Map.of(
                    "actor", ActorDeServicio.desde(autenticacion).orElse(""),
                    "elementoId", elementoId,
                    "subastaId", bloqueo.subastaId(),
                    "propietarioUid", bloqueo.propietarioUid());
        }
    }
}
