package com.nexusbattles.ms_subastas.subastas.realtime;

import com.nexusbattles.ms_subastas.subastas.dto.SubastaResumenResponse;
import com.nexusbattles.ms_subastas.subastas.model.EstadoSubasta;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import com.nexusbattles.ms_subastas.subastas.model.TipoProducto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HU-SUB-011. Prueba de extremo a extremo del canal en vivo.
 *
 * Sincronizacion por espera fija (no por STOMP receipt): el broker simple
 * en memoria de Spring (enableSimpleBroker, ver WebSocketConfig) NO
 * implementa el protocolo de recibos STOMP.
 *
 * JavaTimeModule registrado explicitamente en el ObjectMapper del cliente:
 * a diferencia de Spring MVC (que lo configura automaticamente para las
 * respuestas REST), un MappingJackson2MessageConverter armado a mano para
 * el cliente STOMP no lo trae por defecto -- sin esto, Jackson no sabe
 * deserializar Instant (fechaFin) y falla en silencio del lado del
 * cliente, apareciendo como un TimeoutException generico si no se
 * capturan handleException/handleTransportError.
 *
 * StompSessionHandlerAdapter con handleException/handleTransportError
 * sobrescritos a proposito: sin esto, un error del lado del cliente se
 * traga en silencio y la prueba solo reporta un timeout generico.
 *
 * PENDIENTE DE COORDINAR (ver SubastaActualizadaEvent): esta prueba publica
 * el evento directamente via ApplicationEventPublisher, simulando lo que
 * MotorPujasService haria -- no verifica que Andres ya lo dispare de
 * verdad, porque todavia no lo hace.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class SubastaRealtimePublisherIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @LocalServerPort
    private int puerto;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Test
    void unMensajePublicadoAlEventoLlegaPorElCanalSTOMP() throws Exception {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());

        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.getObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        stompClient.setMessageConverter(converter);

        stompClient.setTaskScheduler(new ConcurrentTaskScheduler());

        String url = "ws://localhost:" + puerto + "/api/v1/ws-subastas";
        StompSession session = stompClient.connectAsync(url, new StompSessionHandlerAdapter() {
                @Override
                public void handleException(StompSession session, StompCommand command,
                                            StompHeaders headers, byte[] payload, Throwable exception) {
                    System.out.println("### STOMP handleException: " + exception);
                    exception.printStackTrace();
                }

                @Override
                public void handleTransportError(StompSession session, Throwable exception) {
                    System.out.println("### STOMP handleTransportError: " + exception);
                    exception.printStackTrace();
                }
            })
            .get(5, TimeUnit.SECONDS);

        CompletableFuture<SubastaResumenResponse> mensajeRecibido = new CompletableFuture<>();

        session.subscribe(
            "/topic/subastas/listado",
            new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return SubastaResumenResponse.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    System.out.println("### Frame recibido, payload=" + payload);
                    mensajeRecibido.complete((SubastaResumenResponse) payload);
                }
            });

        Thread.sleep(2000);
        System.out.println("### Publicando evento...");

        Subasta subasta = new Subasta(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            new BigDecimal("175.00"), new BigDecimal("10.00"), null, null,
            EstadoSubasta.ACTIVA, Instant.now().plusSeconds(1800), 0L);
        subasta.setNombreProducto("Espada del Alba Eterna");
        subasta.setTipoProducto(TipoProducto.ARMA);

        eventPublisher.publishEvent(new SubastaActualizadaEvent(this, subasta));
        System.out.println("### Evento publicado, esperando mensaje...");

        SubastaResumenResponse recibido = mensajeRecibido.get(5, TimeUnit.SECONDS);

        assertEquals("Espada del Alba Eterna", recibido.nombreProducto());
        assertEquals(0, new BigDecimal("175.00").compareTo(recibido.ofertaVigente()));

        session.disconnect();
    }
}
