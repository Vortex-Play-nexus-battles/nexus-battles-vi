package com.nexusbattles.plataforma.salaspartidas.tiemporeal;

import com.nexusbattles.plataforma.salaspartidas.dominio.AccionResuelta;
import com.nexusbattles.plataforma.salaspartidas.dominio.AccionResuelta.Accion;
import com.nexusbattles.plataforma.salaspartidas.dominio.AccionResuelta.Afectado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Adaptador STOMP del canal de partida — HU-SAL-005.
 *
 * <p>Lo que se prueba es la traduccion: destino del canal {@code partidaEstado}
 * y mensaje con exactamente los campos que declara {@code AccionResuelta} en
 * {@code contracts/websocket/salas-partidas.yaml}. Se comprueba tambien lo que
 * NO lleva: ningun color ni porcentaje, porque el umbral es del cliente.
 */
@DisplayName("CanalDePartidaStomp · adaptador del canal de partida (HU-SAL-005)")
class CanalDePartidaStompTest {

    private static final UUID PARTIDA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ANA = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BRUNO = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID MAQUINA = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private final SimpMessagingTemplate mensajeria = mock(SimpMessagingTemplate.class);
    private final CanalDePartidaStomp canal = new CanalDePartidaStomp(mensajeria);

    private static AccionResuelta flechaDoble() {
        return new AccionResuelta(PARTIDA, ANA,
                new Accion("FLECHA_DOBLE", "Flecha doble", null),
                List.of(new Afectado(BRUNO, 55, 100, -25),
                        new Afectado(MAQUINA, 30, 100, -70)));
    }

    private record Publicado(String destino, AvisoDeAccionResuelta aviso) {
    }

    private Publicado capturar() {
        ArgumentCaptor<String> destino = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> cuerpo = ArgumentCaptor.forClass(Object.class);
        verify(mensajeria).convertAndSend(destino.capture(), cuerpo.capture());
        return new Publicado(destino.getValue(), (AvisoDeAccionResuelta) cuerpo.getValue());
    }

    @Test
    @DisplayName("publica en /tema/partidas/{idPartida}, el canal partidaEstado del contrato")
    void publicaEnElDestinoDelContrato() {
        canal.anunciarAccionResuelta(flechaDoble());

        assertEquals("/tema/partidas/" + PARTIDA, capturar().destino());
    }

    @Test
    @DisplayName("el mensaje lleva los cinco campos obligatorios del contrato")
    void elMensajeCumpleElContrato() {
        canal.anunciarAccionResuelta(flechaDoble());

        AvisoDeAccionResuelta aviso = capturar().aviso();
        assertAll(
                () -> assertEquals("partida.accion.resuelta", aviso.tipo()),
                () -> assertEquals(PARTIDA, aviso.idPartida()),
                () -> assertEquals(ANA, aviso.idEjecutor()),
                () -> assertEquals("FLECHA_DOBLE", aviso.accion().codigo()),
                () -> assertEquals("Flecha doble", aviso.accion().nombre()),
                () -> assertNull(aviso.accion().icono()),
                () -> assertEquals(2, aviso.afectados().size()));
    }

    @Test
    @DisplayName("todos los afectados viajan en un solo mensaje, con vida actual y maxima")
    void todosLosAfectadosEnUnMensaje() {
        // Criterio 3: «para todos los participantes». Un mensaje por afectado
        // dejaria las barras desincronizadas durante un instante.
        canal.anunciarAccionResuelta(flechaDoble());

        List<AvisoDeAccionResuelta.Afectado> afectados = capturar().aviso().afectados();
        assertAll(
                () -> assertEquals(BRUNO, afectados.get(0).idJugador()),
                () -> assertEquals(55, afectados.get(0).vidaActual()),
                () -> assertEquals(100, afectados.get(0).vidaMaxima()),
                () -> assertEquals(-25, afectados.get(0).diferencia()),
                () -> assertEquals(MAQUINA, afectados.get(1).idJugador()),
                () -> assertEquals(30, afectados.get(1).vidaActual()),
                () -> assertEquals(-70, afectados.get(1).diferencia()));
    }

    @Test
    @DisplayName("el mensaje no lleva color ni porcentaje: el umbral es del cliente (RF-JUE-009)")
    void sinColorNiPorcentaje() {
        canal.anunciarAccionResuelta(flechaDoble());

        String forma = capturar().aviso().toString();
        assertTrue(!forma.contains("color") && !forma.contains("porcentaje")
                && !forma.contains("estado"), forma);
    }

    @Test
    @DisplayName("cada partida publica en su propio destino")
    void cadaPartidaTieneSuDestino() {
        UUID otra = UUID.fromString("99999999-9999-9999-9999-999999999999");

        assertEquals("/tema/partidas/" + otra, CanalDePartidaStomp.destinoDe(otra));
    }
}
