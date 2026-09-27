package com.nexusbattles.ms_ecommerce.seguridad;

import com.nexusbattles.ms_ecommerce.integracion.ConfiguracionDeIntegraciones;
import com.nexusbattles.ms_ecommerce.integracion.PropiedadesDeLaTienda;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La credencial de servicio contra un emisor HTTP de verdad (un servidor del
 * JDK que habla como {@code POST /api/v1/auth/token} de ms-identidad).
 */
@DisplayName("Credencial de servicio: client_credentials con client_secret_basic y renovacion")
class CredencialDeServicioTest {

    /** Reloj que solo avanza cuando la prueba lo dice. */
    private static final class Reloj extends Clock {
        private Instant ahora = Instant.parse("2026-09-25T12:00:00Z");

        void avanzar(Duration intervalo) {
            ahora = ahora.plus(intervalo);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    private HttpServer emisor;
    private final AtomicInteger emitidos = new AtomicInteger();
    private final List<String> autorizaciones = new CopyOnWriteArrayList<>();
    private final List<String> cuerpos = new CopyOnWriteArrayList<>();
    private volatile int estado = 200;
    private final Reloj reloj = new Reloj();

    @BeforeEach
    void arrancarEmisor() throws IOException {
        emisor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        emisor.createContext("/api/v1/auth/token", intercambio -> {
            autorizaciones.add(intercambio.getRequestHeaders().getFirst("Authorization"));
            cuerpos.add(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String cuerpo = estado == 200
                    ? "{\"access_token\":\"token-" + emitidos.incrementAndGet() + "\",\"token_type\":\"Bearer\",\"expires_in\":900}"
                    : "{\"error\":\"invalid_client\"}";
            byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(estado, bytes.length);
            try (OutputStream salida = intercambio.getResponseBody()) {
                salida.write(bytes);
            }
        });
        emisor.start();
    }

    @AfterEach
    void pararEmisor() {
        emisor.stop(0);
    }

    private CredencialPorClientCredentials credencial() {
        RestClient cliente = ConfiguracionDeIntegraciones.constructor("",
                new PropiedadesDeLaTienda.Http(Duration.ofSeconds(2), Duration.ofSeconds(5))).build();
        return new CredencialPorClientCredentials(cliente,
                "http://127.0.0.1:" + emisor.getAddress().getPort() + "/api/v1/auth/token", "ms-ecommerce",
                "secreto-de-prueba-de-la-tienda", reloj);
    }

    @Test
    @DisplayName("pide el token con Basic (client_id:secreto) y grant_type=client_credentials")
    void pideElToken() {
        assertThat(credencial().portador()).isEqualTo("token-1");

        String esperado = "Basic " + Base64.getEncoder().encodeToString(
                "ms-ecommerce:secreto-de-prueba-de-la-tienda".getBytes(StandardCharsets.UTF_8));
        assertThat(autorizaciones).containsExactly(esperado);
        assertThat(cuerpos).containsExactly("grant_type=client_credentials");
    }

    @Test
    @DisplayName("lo reutiliza mientras vale y lo renueva 30 s antes de caducar")
    void renovacion() {
        CredencialPorClientCredentials credencial = credencial();

        assertThat(credencial.portador()).isEqualTo("token-1");
        reloj.avanzar(Duration.ofSeconds(869));
        assertThat(credencial.portador()).isEqualTo("token-1");
        reloj.avanzar(Duration.ofSeconds(1));
        assertThat(credencial.portador()).isEqualTo("token-2");
        assertThat(emitidos.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("si el emisor rechaza al cliente: credencial no disponible, nunca un token inventado")
    void rechazada() {
        estado = 401;

        assertThatThrownBy(() -> credencial().portador())
                .isInstanceOf(CredencialDeServicioNoDisponibleException.class)
                .hasMessageNotContaining("secreto-de-prueba-de-la-tienda");
    }

    @Test
    @DisplayName("sin URL, client_id o secreto no se construye")
    void configuracionIncompleta() {
        RestClient cliente = RestClient.create();
        assertThatThrownBy(() -> new CredencialPorClientCredentials(cliente, "", "a", "b", reloj))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CredencialPorClientCredentials(cliente, "http://x", " ", "b", reloj))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CredencialPorClientCredentials(cliente, "http://x", "a", null, reloj))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("sin client_id la tienda no tiene credencial: no esta configurada y pedirla falla")
    void sinCredencial() {
        SinCredencialDeServicio sin = new SinCredencialDeServicio();

        assertThat(sin.configurada()).isFalse();
        assertThat(credencial().configurada()).isTrue();
        assertThatThrownBy(sin::portador).isInstanceOf(CredencialDeServicioNoDisponibleException.class);
    }

    @Test
    @DisplayName("el interceptor pone el Bearer si la peticion no traia uno, y respeta el que traiga")
    void interceptor() throws IOException {
        InterceptorDeCredencial interceptor = new InterceptorDeCredencial(new CredencialDeServicio() {
            @Override
            public String portador() {
                return "abc";
            }

            @Override
            public boolean configurada() {
                return true;
            }
        });
        ClientHttpRequestExecution ejecucion = (peticion, cuerpo) -> {
            MockClientHttpResponse respuesta = new MockClientHttpResponse(new byte[0], HttpStatus.OK);
            respuesta.getHeaders().add("X-Autorizacion-Vista", peticion.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            return respuesta;
        };

        ClientHttpResponse sinPrevia = interceptor.intercept(new MockClientHttpRequest(), new byte[0], ejecucion);
        MockClientHttpRequest conPrevia = new MockClientHttpRequest();
        conPrevia.getHeaders().setBearerAuth("otro");
        ClientHttpResponse respetada = interceptor.intercept(conPrevia, new byte[0], ejecucion);

        assertThat(sinPrevia.getHeaders().getFirst("X-Autorizacion-Vista")).isEqualTo("Bearer abc");
        assertThat(respetada.getHeaders().getFirst("X-Autorizacion-Vista")).isEqualTo("Bearer otro");
    }
}
