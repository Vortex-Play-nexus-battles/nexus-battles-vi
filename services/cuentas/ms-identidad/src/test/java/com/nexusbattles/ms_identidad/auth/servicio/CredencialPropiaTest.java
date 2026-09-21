package com.nexusbattles.ms_identidad.auth.servicio;

import com.nexusbattles.ms_identidad.auth.service.ClavesDeFirma;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CredencialPropia · ms-identidad se firma su propia credencial de servicio y la renueva a tiempo")
class CredencialPropiaTest {

    /** Reloj que se mueve a mano, como en el resto de la plataforma. */
    private static final class RelojDeMano extends Clock {
        private Instant ahora = Instant.now();

        void avanzar(Duration cuanto) {
            ahora = ahora.plus(cuanto);
        }

        @Override
        public Instant instant() {
            return ahora;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zona) {
            return this;
        }
    }

    private final ClavesDeFirma claves = new ClavesDeFirma("");
    private final RelojDeMano reloj = new RelojDeMano();
    private final EmisorDeTokensDeServicio emisor = new EmisorDeTokensDeServicio(claves, "ms-identidad", 15, reloj);
    private final CredencialPropia credencial = new CredencialPropia(emisor, reloj);

    private Claims claimsDe(String token) {
        return Jwts.parser().verifyWith(claves.publica()).build().parseSignedClaims(token).getPayload();
    }

    @Test
    @DisplayName("el portador es un token de servicio de ms-identidad verificable con su clave publica")
    void portador() {
        Claims claims = claimsDe(credencial.portador());

        assertEquals(CredencialPropia.CLIENT_ID, claims.getSubject());
        assertEquals(CredencialPropia.CLIENT_ID, claims.get("azp", String.class));
        assertEquals("SERVICIO", claims.get("rol", String.class));
    }

    @Test
    @DisplayName("reutiliza el token mientras vale y lo renueva 30 segundos antes de caducar")
    void reutilizaYRenueva() {
        String primero = credencial.portador();

        reloj.avanzar(Duration.ofMinutes(10));
        assertEquals(primero, credencial.portador(), "a los 10 minutos de 15 sigue valiendo el mismo");

        reloj.avanzar(Duration.ofMinutes(4).plusSeconds(45));
        String segundo = credencial.portador();
        assertNotEquals(primero, segundo, "a 15 segundos de caducar ya se renovo");
        assertTrue(claimsDe(segundo).getExpiration().toInstant().isAfter(claimsDe(primero).getExpiration().toInstant()));
    }

    @Test
    @DisplayName("como interceptor pone Authorization: Bearer <token> en cada peticion saliente")
    void interceptor() throws IOException {
        MockClientHttpRequest peticion = new MockClientHttpRequest(HttpMethod.POST, URI.create("http://correo/api/v1/correos/bienvenida"));
        AtomicReference<HttpRequest> vista = new AtomicReference<>();
        ClientHttpRequestExecution ejecucion = (req, cuerpo) -> {
            vista.set(req);
            return new MockClientHttpResponse(new byte[0], 202);
        };

        ClientHttpResponse respuesta = credencial.intercept(peticion, new byte[0], ejecucion);

        assertEquals(202, respuesta.getStatusCode().value());
        String autorizacion = vista.get().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        assertTrue(autorizacion.startsWith("Bearer "));
        assertEquals(CredencialPropia.CLIENT_ID, claimsDe(autorizacion.substring(7)).getSubject());
    }
}
