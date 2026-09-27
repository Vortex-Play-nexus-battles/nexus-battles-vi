package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.integracion;

import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.DirectorioDeJugadores.CuentaDeJugador;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.DirectorioDeJugadores.DirectorioNoDisponible;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("ClienteDirectorioDeIdentidad · el destinatario existe y esta activo (B6)")
class ClienteDirectorioDeIdentidadTest {

    private static final String BASE = "http://localhost:8089/";
    private static final UUID BRUNO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String CONSULTA = "http://localhost:8089/api/v1/internal/usuarios/" + BRUNO + "/contacto";

    private static final class Reloj extends Clock {
        Instant ahora = Instant.parse("2026-09-25T18:00:00Z");

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

    private final Reloj reloj = new Reloj();
    private MockRestServiceServer servidor;
    private ClienteDirectorioDeIdentidad cliente;

    @BeforeEach
    void montar() {
        RestClient.Builder constructor = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(constructor).build();
        cliente = new ClienteDirectorioDeIdentidad(constructor.build(), BASE, Duration.ofSeconds(30), reloj);
    }

    private static String contacto(String estado) {
        return "{\"uid\":\"" + BRUNO + "\",\"email\":\"bruno@nexus.test\",\"apodo\":\"bruno\",\"estado\":\""
                + estado + "\"}";
    }

    @Test
    @DisplayName("pregunta por el GET del contrato y se queda con apodo y estado; el correo ni se lee")
    void cuentaActiva() {
        servidor.expect(requestTo(CONSULTA)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(contacto("ACTIVO"), MediaType.APPLICATION_JSON));

        Optional<CuentaDeJugador> cuenta = cliente.buscar(BRUNO);

        assertAll(
                () -> assertTrue(cuenta.isPresent()),
                () -> assertEquals("bruno", cuenta.get().apodo()),
                () -> assertTrue(cuenta.get().activa()),
                () -> assertFalse(cuenta.get().toString().contains("@"), "el correo no se guarda"));
        servidor.verify();
    }

    @Test
    @DisplayName("una cuenta suspendida existe pero no esta activa")
    void cuentaNoActiva() {
        servidor.expect(requestTo(CONSULTA)).andRespond(withSuccess(contacto("SUSPENDIDO"), MediaType.APPLICATION_JSON));
        assertFalse(cliente.buscar(BRUNO).orElseThrow().activa());
    }

    @Test
    @DisplayName("un 404 es «no existe», no un fallo")
    void noExiste() {
        servidor.expect(requestTo(CONSULTA)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertTrue(cliente.buscar(BRUNO).isEmpty());
    }

    @Test
    @DisplayName("si identidad no responde, o rechaza la credencial, es «no se pudo preguntar»")
    void noDisponible() {
        servidor.expect(requestTo(CONSULTA)).andRespond(withServerError());
        servidor.expect(requestTo(CONSULTA)).andRespond(withStatus(HttpStatus.FORBIDDEN));
        servidor.expect(requestTo(CONSULTA)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertAll(
                () -> assertThrows(DirectorioNoDisponible.class, () -> cliente.buscar(BRUNO)),
                () -> assertThrows(DirectorioNoDisponible.class, () -> cliente.buscar(BRUNO)),
                () -> assertThrows(DirectorioNoDisponible.class, () -> cliente.buscar(BRUNO), "sin estado"));
    }

    @Test
    @DisplayName("recuerda la respuesta un rato (tambien el «no existe») y luego vuelve a preguntar")
    void cacheCorta() {
        servidor.expect(ExpectedCount.twice(), requestTo(CONSULTA))
                .andRespond(withSuccess(contacto("ACTIVO"), MediaType.APPLICATION_JSON));

        cliente.buscar(BRUNO);
        cliente.buscar(BRUNO);
        reloj.ahora = reloj.ahora.plusSeconds(31);
        cliente.buscar(BRUNO);

        servidor.verify();
    }

    @Test
    @DisplayName("un fallo no se recuerda: a la siguiente se pregunta otra vez")
    void losFallosNoSeRecuerdan() {
        servidor.expect(requestTo(CONSULTA)).andRespond(withServerError());
        servidor.expect(requestTo(CONSULTA)).andRespond(withSuccess(contacto("ACTIVO"), MediaType.APPLICATION_JSON));

        assertThrows(DirectorioNoDisponible.class, () -> cliente.buscar(BRUNO));
        assertTrue(cliente.buscar(BRUNO).isPresent());
    }

    @Test
    @DisplayName("sin vigencia no hay cache")
    void sinCache() {
        RestClient.Builder constructor = RestClient.builder();
        MockRestServiceServer otro = MockRestServiceServer.bindTo(constructor).build();
        ClienteDirectorioDeIdentidad sinMemoria =
                new ClienteDirectorioDeIdentidad(constructor.build(), BASE, Duration.ZERO, reloj);
        otro.expect(ExpectedCount.twice(), requestTo(CONSULTA)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        sinMemoria.buscar(BRUNO);
        sinMemoria.buscar(BRUNO);

        otro.verify();
    }

    @Test
    @DisplayName("la ruta es la que publica ms-identidad-admin.yaml")
    void rutaDelContrato() throws Exception {
        String contrato = Files.readString(Path.of("../../../contracts/openapi/ms-identidad-admin.yaml"));
        assertTrue(contrato.contains(ClienteDirectorioDeIdentidad.RUTA + ":"), ClienteDirectorioDeIdentidad.RUTA);
    }
}
