package com.nexusbattles.plataforma.comentarios.catalogo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.nexusbattles.plataforma.comentarios.catalogo.CatalogoDeProductos.Existencia;

/**
 * El cliente del catalogo de productos — B3.
 *
 * <p>Lo que importa: que solo un 404 dice «no existe», que solo un 200 con el
 * mismo id dice «existe», que cualquier otra cosa es «no se sabe» (y las
 * escrituras no se hacen a ciegas), y que la cache recuerda cada respuesta el
 * tiempo que le toca y nunca recuerda una caida.
 */
class ClienteCatalogoTest {

    private static final String BASE = "http://productos.prueba";
    private static final String PRODUCTO = "1647b2ea-096d-37e7-b580-0172e4c62313";
    private static final String URL = BASE + "/api/v1/productos/" + PRODUCTO;

    private MockRestServiceServer servidor;
    private RelojMovil reloj;
    private ClienteCatalogo cliente;

    @BeforeEach
    void montar() {
        RestClient.Builder constructor = RestClient.builder().baseUrl(BASE);
        servidor = MockRestServiceServer.bindTo(constructor).build();
        reloj = new RelojMovil(Instant.parse("2026-09-25T10:00:00Z"));
        cliente = new ClienteCatalogo(constructor.build(), reloj, Duration.ofMinutes(5), Duration.ofSeconds(30));
    }

    private static String producto(String id) {
        return "{\"id\":\"" + id + "\",\"nombre\":\"Espada de una mano\",\"tipo\":\"ARMA\",\"estado\":\"ACTIVO\"}";
    }

    @Nested
    @DisplayName("que respuesta significa que")
    class Significados {

        @Test
        @DisplayName("200 con el mismo id: existe, y la pregunta es el GET publico del contrato")
        void existe() {
            servidor.expect(requestTo(URL))
                    .andExpect(method(HttpMethod.GET))
                    .andExpect(header("Accept", MediaType.APPLICATION_JSON_VALUE))
                    .andRespond(withSuccess(producto(PRODUCTO), MediaType.APPLICATION_JSON));

            assertEquals(Existencia.EXISTE, cliente.existencia(PRODUCTO));
            assertDoesNotThrow(() -> cliente.exigirExistente(PRODUCTO));
            servidor.verify();
        }

        @Test
        @DisplayName("404: no existe, y exigirlo es ProductoInexistente")
        void noExiste() {
            servidor.expect(requestTo(URL)).andRespond(withResourceNotFound());

            assertThrows(ProductoInexistente.class, () -> cliente.exigirExistente(PRODUCTO));
            assertEquals(Existencia.NO_EXISTE, cliente.existencia(PRODUCTO), "la segunda sale de la cache");
            servidor.verify();
        }

        @Test
        @DisplayName("5xx, otro 4xx o un tiempo agotado: no se sabe, y exigirlo es 503")
        void desconocida() {
            servidor.expect(requestTo(URL)).andRespond(withServerError());
            assertEquals(Existencia.DESCONOCIDA, cliente.existencia(PRODUCTO));

            servidor.reset();
            servidor.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
            assertEquals(Existencia.DESCONOCIDA, cliente.existencia(PRODUCTO));

            servidor.reset();
            servidor.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("lectura")));
            assertThrows(CatalogoNoDisponible.class, () -> cliente.exigirExistente(PRODUCTO));
        }

        @Test
        @DisplayName("un 200 que no es ese producto (un eco, una pagina) no se cree")
        void doscientosQueNoEsElProducto() {
            servidor.expect(requestTo(URL))
                    .andRespond(withSuccess(producto("otro-id"), MediaType.APPLICATION_JSON));
            assertEquals(Existencia.DESCONOCIDA, cliente.existencia(PRODUCTO));

            servidor.reset();
            servidor.expect(requestTo(URL)).andRespond(withSuccess("<html>hola</html>", MediaType.TEXT_HTML));
            assertEquals(Existencia.DESCONOCIDA, cliente.existencia(PRODUCTO));

            servidor.reset();
            servidor.expect(requestTo(URL)).andRespond(withException(new IOException("conexion rechazada")));
            assertEquals(Existencia.DESCONOCIDA, cliente.existencia(PRODUCTO));
        }

        @Test
        @DisplayName("un id vacio o de mas de 64 caracteres no existe y ni se pregunta")
        void idImposible() {
            assertEquals(Existencia.NO_EXISTE, cliente.existencia(" "));
            assertEquals(Existencia.NO_EXISTE, cliente.existencia(null));
            assertEquals(Existencia.NO_EXISTE, cliente.existencia("x".repeat(65)));
            servidor.verify();
        }
    }

    @Nested
    @DisplayName("la cache")
    class Cache {

        @Test
        @DisplayName("un «existe» se recuerda 5 minutos: dentro no se pregunta, despues si")
        void positivos() {
            servidor.expect(times(2), requestTo(URL))
                    .andRespond(withSuccess(producto(PRODUCTO), MediaType.APPLICATION_JSON));

            cliente.existencia(PRODUCTO);
            reloj.avanzar(Duration.ofMinutes(4).plusSeconds(59));
            assertEquals(Existencia.EXISTE, cliente.existencia(PRODUCTO));
            reloj.avanzar(Duration.ofSeconds(1));
            assertEquals(Existencia.EXISTE, cliente.existencia(PRODUCTO));
            servidor.verify();
        }

        @Test
        @DisplayName("un «no existe» solo 30 segundos: el producto puede darse de alta justo despues")
        void negativos() {
            servidor.expect(once(), requestTo(URL)).andRespond(withResourceNotFound());
            servidor.expect(once(), requestTo(URL))
                    .andRespond(withSuccess(producto(PRODUCTO), MediaType.APPLICATION_JSON));

            assertEquals(Existencia.NO_EXISTE, cliente.existencia(PRODUCTO));
            reloj.avanzar(Duration.ofSeconds(29));
            assertEquals(Existencia.NO_EXISTE, cliente.existencia(PRODUCTO));
            reloj.avanzar(Duration.ofSeconds(1));
            assertEquals(Existencia.EXISTE, cliente.existencia(PRODUCTO), "ya dado de alta");
            servidor.verify();
        }

        @Test
        @DisplayName("una caida no se recuerda: la siguiente peticion vuelve a preguntar")
        void caidasNo() {
            servidor.expect(once(), requestTo(URL)).andRespond(withServerError());
            servidor.expect(once(), requestTo(URL))
                    .andRespond(withSuccess(producto(PRODUCTO), MediaType.APPLICATION_JSON));

            assertEquals(Existencia.DESCONOCIDA, cliente.existencia(PRODUCTO));
            assertEquals(Existencia.EXISTE, cliente.existencia(PRODUCTO));
            servidor.verify();
        }

        @Test
        @DisplayName("tiene tope: llena, tira primero las vencidas y, si no basta, todas")
        void tope() {
            CacheDeExistencia cache = new CacheDeExistencia(Duration.ofMinutes(5), Duration.ofSeconds(30), reloj);
            for (int i = 0; i < CacheDeExistencia.CAPACIDAD; i++) {
                cache.recordar("p-" + i, i % 2 == 0 ? Existencia.EXISTE : Existencia.NO_EXISTE);
            }
            assertEquals(CacheDeExistencia.CAPACIDAD, cache.tamano());

            // Vencen las negativas (30 s): al llegar al tope se tiran solo esas.
            reloj.avanzar(Duration.ofSeconds(31));
            cache.recordar("nuevo", Existencia.EXISTE);
            assertEquals(CacheDeExistencia.CAPACIDAD / 2 + 1, cache.tamano());

            // Sin nada vencido y en el tope, se vacia entera.
            CacheDeExistencia llena = new CacheDeExistencia(Duration.ofMinutes(5), Duration.ofMinutes(5), reloj);
            for (int i = 0; i < CacheDeExistencia.CAPACIDAD; i++) {
                llena.recordar("q-" + i, Existencia.EXISTE);
            }
            llena.recordar("otro", Existencia.EXISTE);
            assertEquals(1, llena.tamano());
            llena.recordar("caido", Existencia.DESCONOCIDA);
            assertEquals(1, llena.tamano(), "una caida no se guarda");
        }
    }

    /** Un reloj que se mueve cuando la prueba lo dice. */
    static final class RelojMovil extends Clock {

        private Instant ahora;

        RelojMovil(Instant inicio) {
            this.ahora = inicio;
        }

        void avanzar(Duration cuanto) {
            ahora = ahora.plus(cuanto);
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
}
