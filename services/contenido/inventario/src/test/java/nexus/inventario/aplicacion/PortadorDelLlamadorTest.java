package nexus.inventario.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** B4 — el token que se reenvia al catalogo es el de quien llamo, y solo si es un JWT. */
class PortadorDelLlamadorTest {

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("con un JWT en la peticion, devuelve su valor")
    void conJwt() {
        Jwt token = new Jwt("valor-del-token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "RS256"), Map.of("sub", "ms-ecommerce"));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token));

        assertEquals("valor-del-token", PortadorDelLlamador.actual().orElseThrow());
    }

    @Test
    @DisplayName("sin autenticacion, o con una que no es un JWT, no hay nada que reenviar")
    void sinJwt() {
        assertTrue(PortadorDelLlamador.actual().isEmpty());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alguien", "no-es-un-token"));

        assertTrue(PortadorDelLlamador.actual().isEmpty());
    }
}
