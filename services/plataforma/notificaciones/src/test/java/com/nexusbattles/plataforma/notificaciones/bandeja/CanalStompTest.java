package com.nexusbattles.plataforma.notificaciones.bandeja;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.nexusbattles.plataforma.notificaciones.Notificacion;

/**
 * Pruebas del canal STOMP contra los destinos que fija el contrato AsyncAPI.
 *
 * Sin servidor ni broker: la plantilla de mensajeria se sustituye por un doble.
 * Lo que importa verificar es que se publique en la cola privada del jugador y
 * no en un tema de difusion, porque de eso depende que un aviso llegue a todas
 * las sesiones de esa persona y a nadie mas.
 */
@ExtendWith(MockitoExtension.class)
class CanalStompTest {

    private static final String DESTINO = "/cola/notificaciones";
    private static final String JUGADOR = "jugador-1";
    private static final Instant AYER = Instant.parse("2026-08-30T15:00:00Z");

    @Mock
    private SimpMessagingTemplate mensajeria;

    @InjectMocks
    private CanalStomp canal;

    private static Notificacion aviso(String id) {
        return new Notificacion(id, "subasta", "Tu puja fue superada",
                "Alguien pujo mas alto.", AYER);
    }

    @Test
    @DisplayName("avisar publica el aviso en la cola privada y ademas el contador")
    void avisarPublicaAvisoYContador() {
        canal.avisar(JUGADOR, aviso("evt-1"), 2);

        verify(mensajeria).convertAndSendToUser(
                eq(JUGADOR), eq(DESTINO), any(Notificacion.class));
        verify(mensajeria).convertAndSendToUser(
                eq(JUGADOR), eq(DESTINO), eq((Object) Map.of("noLeidas", 2)));
    }

    @Test
    @DisplayName("los pendientes se publican uno por uno y al final el contador")
    void entregarPendientesPublicaCadaAviso() {
        canal.entregarPendientes(JUGADOR, List.of(aviso("evt-1"), aviso("evt-2")), 5);

        verify(mensajeria, times(2)).convertAndSendToUser(
                eq(JUGADOR), eq(DESTINO), any(Notificacion.class));
        verify(mensajeria).convertAndSendToUser(
                eq(JUGADOR), eq(DESTINO), eq((Object) Map.of("noLeidas", 5)));
    }

    @Test
    @DisplayName("sin pendientes solo viaja el contador, no se publica ningun aviso")
    void sinPendientesSoloViajaElContador() {
        canal.entregarPendientes(JUGADOR, List.of(), 0);

        verify(mensajeria, never()).convertAndSendToUser(
                eq(JUGADOR), eq(DESTINO), any(Notificacion.class));
        verify(mensajeria).convertAndSendToUser(
                eq(JUGADOR), eq(DESTINO), eq((Object) Map.of("noLeidas", 0)));
    }

    @Test
    @DisplayName("el contador se publica con la cuenta que recibe")
    void actualizarContadorPublicaLaCuenta() {
        canal.actualizarContador(JUGADOR, 7);

        verify(mensajeria).convertAndSendToUser(
                eq(JUGADOR), eq(DESTINO), eq((Object) Map.of("noLeidas", 7)));
    }
}
