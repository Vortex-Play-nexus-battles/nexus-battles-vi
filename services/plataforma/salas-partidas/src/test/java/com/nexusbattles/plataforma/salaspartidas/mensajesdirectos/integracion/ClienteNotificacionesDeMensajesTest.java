package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.integracion;

import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.Conversacion;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.MensajeDirecto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

@DisplayName("ClienteNotificacionesDeMensajes · aviso en la bandeja, sin el texto (B6)")
class ClienteNotificacionesDeMensajesTest {

    private static final String BASE = "http://localhost:8085/api/v1/";
    private static final String DESTINO = "http://localhost:8085/api/v1/internal/notifications";
    private static final UUID ANA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BRUNO = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final MensajeDirecto mensaje = new MensajeDirecto(
            UUID.fromString("99999999-9999-9999-9999-999999999999"), Conversacion.entre(ANA, BRUNO), ANA, "ana",
            BRUNO, "bruno", "el texto es privado", Instant.parse("2026-09-25T18:00:00Z"), null, "cli-1");

    /** El primer no leido de la racha: otro mensaje, anterior a este. */
    private static final UUID RACHA = UUID.fromString("88888888-8888-8888-8888-888888888888");

    private RestClient.Builder constructor;
    private MockRestServiceServer servidor;

    @BeforeEach
    void montar() {
        constructor = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(constructor).build();
    }

    private ClienteNotificacionesDeMensajes cliente(String base) {
        return new ClienteNotificacionesDeMensajes(constructor.build(), base, Runnable::run);
    }

    @Test
    @DisplayName("manda el EmitirNotificacionRequest del contrato: al destinatario, con el id de la racha y sin el texto")
    void cuerpoDelContrato() {
        servidor.expect(requestTo(DESTINO))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.usuarioId").value(BRUNO.toString()))
                // El id es el de la racha, no el de este mensaje: todos los de
                // la misma racha repiten id y notificaciones deja uno (409).
                .andExpect(jsonPath("$.id").value("mensaje-directo-88888888-8888-8888-8888-888888888888"))
                .andExpect(jsonPath("$.tipo").value("MENSAJE_PRIVADO"))
                .andExpect(jsonPath("$.titulo").value("Nuevo mensaje privado"))
                .andExpect(jsonPath("$.cuerpo").value("ana te escribió un mensaje privado."))
                .andExpect(jsonPath("$.creadaEn").value("2026-09-25T18:00:00Z"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("el texto es privado"))))
                .andRespond(withStatus(HttpStatus.CREATED));

        cliente(BASE).avisar(mensaje, RACHA);
        servidor.verify();
    }

    @Test
    @DisplayName("un 409 es que ya estaba; un 5xx se pierde el aviso, no el mensaje: ninguno lanza")
    void nuncaLanza() {
        servidor.expect(requestTo(DESTINO)).andRespond(withStatus(HttpStatus.CONFLICT));
        servidor.expect(requestTo(DESTINO)).andRespond(withServerError());
        ClienteNotificacionesDeMensajes cliente = cliente(BASE);

        assertDoesNotThrow(() -> cliente.avisar(mensaje, RACHA));
        assertDoesNotThrow(() -> cliente.avisar(mensaje, RACHA));
        servidor.verify();
    }

    @Test
    @DisplayName("sin URL de notificaciones no se manda nada")
    void sinUrl() {
        servidor.expect(never(), requestTo(DESTINO));
        cliente("  ").avisar(mensaje, RACHA);
        cliente(null).avisar(mensaje, RACHA);
        servidor.verify();
    }

    @Test
    @DisplayName("si no hay hilo para mandarlo, se pierde el aviso sin romper el envio")
    void sinHilo() {
        ClienteNotificacionesDeMensajes sinEjecutor = new ClienteNotificacionesDeMensajes(constructor.build(), BASE,
                tarea -> {
                    throw new RejectedExecutionException("apagando");
                });
        assertDoesNotThrow(() -> sinEjecutor.avisar(mensaje, RACHA));
    }
}
