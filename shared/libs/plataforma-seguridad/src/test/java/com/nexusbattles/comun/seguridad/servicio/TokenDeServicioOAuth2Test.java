package com.nexusbattles.comun.seguridad.servicio;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ADR-001 — obtencion, cache y renovacion de la credencial de servicio.
 *
 * <p>Se prueba contra un emisor falso que solo sabe responder al endpoint de
 * token: lo que interesa es que el cliente pida lo correcto, reutilice lo que
 * tiene y renueve antes de caducar. La validez criptografica del token la
 * prueba {@code PatronServicioAServicioIT} con un Keycloak real.
 */
@DisplayName("TokenDeServicioOAuth2 · credencial de servicio por client_credentials")
class TokenDeServicioOAuth2Test {

    /**
     * Reloj que se mueve a mano, como en el resto de la plataforma.
     *
     * <p>Arranca en la hora real y no en una fecha fija: Spring fecha el token
     * ({@code issuedAt}) con el reloj del sistema, asi que la caducidad se
     * juzga comparando este reloj contra esa hora real. Avanzarlo a mano es lo
     * que permite probar la renovacion sin dormir hilos.
     */
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

    private EmisorFalso emisor;
    private final RelojDeMano reloj = new RelojDeMano();

    @BeforeEach
    void levantarEmisor() throws IOException {
        emisor = new EmisorFalso();
    }

    @AfterEach
    void apagarEmisor() {
        emisor.close();
    }

    private TokenDeServicioOAuth2 token() {
        return new TokenDeServicioOAuth2(emisor.urlDelRealm(), "ms-subastas", "secreto", reloj);
    }

    @Test
    @DisplayName("pide el token con client_credentials y autenticacion basica del cliente, sin ningun usuario")
    void pideElTokenComoServicio() {
        String portador = token().portador();

        assertThat(portador).isEqualTo("t1");
        EmisorFalso.Peticion peticion = emisor.peticiones().get(0);
        assertThat(peticion.cuerpo()).contains("grant_type=client_credentials");
        assertThat(peticion.authorization()).isEqualTo(EmisorFalso.basic("ms-subastas", "secreto"));
        // Nada de un jugador viaja al emisor: ni username, ni password, ni token ajeno.
        assertThat(peticion.cuerpo()).doesNotContain("username").doesNotContain("password")
                .doesNotContain("assertion");
    }

    @Test
    @DisplayName("reutiliza el token mientras esta vigente: una sola ida al emisor")
    void reutilizaMientrasEstaVigente() {
        TokenDeServicioOAuth2 servicio = token();

        String primero = servicio.portador();
        reloj.avanzar(Duration.ofSeconds(100));
        String segundo = servicio.portador();

        assertThat(segundo).isEqualTo(primero);
        assertThat(emisor.emitidos()).isEqualTo(1);
    }

    @Test
    @DisplayName("renueva antes de que caduque, con margen, sin que nadie se lo pida")
    void renuevaAntesDeCaducar() {
        emisor.expiraEn(300);
        TokenDeServicioOAuth2 servicio = token();

        assertThat(servicio.portador()).isEqualTo("t1");
        // Caduca a los 300 s; con 30 s de margen se renueva a partir de los 270.
        // Los 10 s de holgura a cada lado absorben lo que tarde el emisor falso.
        reloj.avanzar(Duration.ofSeconds(260));
        assertThat(servicio.portador()).isEqualTo("t1");
        reloj.avanzar(Duration.ofSeconds(20));
        assertThat(servicio.portador()).isEqualTo("t2");

        assertThat(emisor.emitidos()).isEqualTo(2);
    }

    @Test
    @DisplayName("funciona desde un hilo sin SecurityContext, como un @Scheduled")
    void funcionaSinContextoDeSeguridad() throws Exception {
        TokenDeServicioOAuth2 servicio = token();
        String[] obtenido = new String[1];

        Thread hilo = new Thread(() -> obtenido[0] = servicio.portador(), "job-de-cierre");
        hilo.start();
        hilo.join(Duration.ofSeconds(10));

        assertThat(obtenido[0]).isEqualTo("t1");
    }

    @Test
    @DisplayName("si el emisor rechaza al servicio, falla con un error de disponibilidad y no inventa un token")
    void siElEmisorRechazaNoInventa() {
        emisor.rechazarCredenciales();

        assertThatThrownBy(() -> token().portador())
                .isInstanceOf(CredencialDeServicioNoDisponible.class)
                .hasMessageContaining("ms-subastas");
    }

    @Test
    @DisplayName("el endpoint de token es el estandar OIDC del realm")
    void endpointDeToken() {
        assertThat(TokenDeServicioOAuth2.endpointDeToken("https://kc/realms/nexus-battles"))
                .isEqualTo("https://kc/realms/nexus-battles/protocol/openid-connect/token");
        assertThat(TokenDeServicioOAuth2.endpointDeToken("https://kc/realms/nexus-battles/"))
                .isEqualTo("https://kc/realms/nexus-battles/protocol/openid-connect/token");
    }

    @Test
    @DisplayName("sin URL, client_id o secreto no arranca: mejor fallar al construir que en la primera llamada")
    void configuracionIncompleta() {
        assertThatThrownBy(() -> new TokenDeServicioOAuth2("", "id", "s", reloj))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("DIRECTORIO_ACTIVO_URL");
        assertThatThrownBy(() -> new TokenDeServicioOAuth2("http://kc/realms/x", " ", "s", reloj))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("CLIENT_ID");
        assertThatThrownBy(() -> new TokenDeServicioOAuth2("http://kc/realms/x", "id", null, reloj))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("CLIENT_SECRET");
    }
}
