package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.canal;

import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.Conversacion;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.MensajeDirecto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("EntregaStomp · la cola de usuario de cada uno (B6)")
class EntregaStompTest {

    private static final UUID ANA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BRUNO = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final SimpMessagingTemplate plantilla = mock(SimpMessagingTemplate.class);
    private final SimpUserRegistry registro = mock(SimpUserRegistry.class);
    private final EntregaStomp entrega = new EntregaStomp(plantilla, registro);

    private final MensajeDirecto mensaje = new MensajeDirecto(UUID.randomUUID(), Conversacion.entre(ANA, BRUNO), ANA,
            "ana", BRUNO, "bruno", "hola", Instant.parse("2026-09-25T18:00:00Z"), null, "cli-1");

    @Test
    @DisplayName("entrega al destinatario y al remitente; el idCliente solo en el eco")
    void aLosDos() {
        entrega.entregar(mensaje);

        ArgumentCaptor<Object> aBruno = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> aAna = ArgumentCaptor.forClass(Object.class);
        verify(plantilla).convertAndSendToUser(eq(BRUNO.toString()), eq("/cola/mensajes-directos"), aBruno.capture());
        verify(plantilla).convertAndSendToUser(eq(ANA.toString()), eq("/cola/mensajes-directos"), aAna.capture());

        MensajeEntregadoPayload paraBruno = (MensajeEntregadoPayload) aBruno.getValue();
        MensajeEntregadoPayload paraAna = (MensajeEntregadoPayload) aAna.getValue();
        assertAll(
                () -> assertEquals("MENSAJE", paraBruno.tipo()),
                () -> assertEquals(mensaje.id(), paraBruno.id()),
                () -> assertEquals(mensaje.conversacion().clave(), paraBruno.conversacion()),
                () -> assertEquals(ANA, paraBruno.remitente()),
                () -> assertEquals("ana", paraBruno.apodoRemitente()),
                () -> assertEquals(BRUNO, paraBruno.destinatario()),
                () -> assertEquals("hola", paraBruno.texto()),
                () -> assertEquals(mensaje.enviadoEn(), paraBruno.fecha()),
                () -> assertNull(paraBruno.idCliente(), "el idCliente es del autor"),
                () -> assertEquals("cli-1", paraAna.idCliente()));
    }

    @Test
    @DisplayName("un reintento solo repite el eco al remitente")
    void soloEco() {
        entrega.reenviarEco(mensaje);

        verify(plantilla).convertAndSendToUser(eq(ANA.toString()), eq("/cola/mensajes-directos"), any(Object.class));
        verify(plantilla, never()).convertAndSendToUser(eq(BRUNO.toString()), any(), any(Object.class));
    }

    @Test
    @DisplayName("escucha quien tiene una sesion suscrita a su cola de mensajes privados, no quien solo esta conectado")
    void quienEscucha() {
        SimpUser conCola = usuarioSuscritoA("/usuario/cola/mensajes-directos");
        SimpUser soloSala = usuarioSuscritoA("/usuario/cola/salas");
        UUID carla = UUID.randomUUID();
        when(registro.getUser(BRUNO.toString())).thenReturn(conCola);
        when(registro.getUser(carla.toString())).thenReturn(soloSala);

        assertAll(
                () -> assertTrue(entrega.estaEscuchando(BRUNO)),
                () -> assertFalse(entrega.estaEscuchando(carla), "en el combate no lee sus mensajes"),
                () -> assertFalse(entrega.estaEscuchando(ANA), "sin sesion no escucha"));
    }

    private static SimpUser usuarioSuscritoA(String destino) {
        SimpSubscription suscripcion = mock(SimpSubscription.class);
        when(suscripcion.getDestination()).thenReturn(destino);
        SimpSession sesion = mock(SimpSession.class);
        when(sesion.getSubscriptions()).thenReturn(Set.of(suscripcion));
        SimpUser usuario = mock(SimpUser.class);
        when(usuario.getSessions()).thenReturn(Set.of(sesion));
        return usuario;
    }
}
