package com.nexusbattles.plataforma.salaspartidas.tiemporeal;

import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Adaptador STOMP del canal de sala — HU-SAL-002.
 *
 * <p>Lo que se prueba es la traduccion, que es su unico trabajo: que publique
 * en el destino del canal {@code salaEstado} y que el mensaje tenga
 * exactamente los cuatro campos que declara {@code ParticipanteIngreso} en
 * {@code contracts/websocket/salas-partidas.yaml}.
 *
 * <p>Se comprueba tambien lo que NO lleva. El contrato se corrigio en esta
 * misma historia para que dejara de reutilizar el esquema {@code Participante},
 * que exigia heroe y vida: datos que este servicio no tiene y que la sala de
 * espera no muestra.
 */
@DisplayName("CanalDeSalaStomp · adaptador del canal de sala (HU-SAL-002)")
class CanalDeSalaStompTest {

    private static final UUID ANFITRION = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID VISITANTE = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final SimpMessagingTemplate mensajeria = mock(SimpMessagingTemplate.class);
    private final CanalDeSalaStomp canal = new CanalDeSalaStomp(mensajeria);

    private static Sala salaConDosDentro() {
        Sala sala = Sala.crear(
                new ParametrosDeSala(6, Modalidad.HASTA_SEIS, 320, false, false, null), ANFITRION);
        sala.unirse(VISITANTE);
        return sala;
    }

    /** Lo publicado: destino y cuerpo, capturados de una sola llamada. */
    private record Publicado(String destino, AvisoDeIngreso aviso) {
    }

    private Publicado capturar() {
        ArgumentCaptor<String> destino = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> cuerpo = ArgumentCaptor.forClass(Object.class);
        verify(mensajeria).convertAndSend(destino.capture(), cuerpo.capture());
        return new Publicado(destino.getValue(), (AvisoDeIngreso) cuerpo.getValue());
    }

    @Test
    @DisplayName("publica en /tema/salas/{idSala}, el canal salaEstado del contrato")
    void publicaEnElDestinoDelContrato() {
        Sala sala = salaConDosDentro();

        canal.anunciarIngreso(sala, VISITANTE);

        assertEquals("/tema/salas/" + sala.id(), capturar().destino());
    }

    @Test
    @DisplayName("el mensaje lleva los cuatro campos obligatorios del contrato")
    void elMensajeCumpleElContrato() {
        Sala sala = salaConDosDentro();

        canal.anunciarIngreso(sala, VISITANTE);

        AvisoDeIngreso aviso = capturar().aviso();
        assertAll(
                () -> assertEquals("sala.participante.ingreso", aviso.tipo()),
                () -> assertEquals(sala.id(), aviso.idSala()),
                () -> assertEquals(VISITANTE, aviso.idJugador()),
                () -> assertEquals(2, aviso.ocupacion().actual()),
                () -> assertEquals(6, aviso.ocupacion().maximo()));
    }

    @Test
    @DisplayName("la ocupacion anunciada sale de la sala, no es un numero aparte")
    void laOcupacionSaleDeLaSala() {
        Sala sala = salaConDosDentro();
        sala.unirse(UUID.randomUUID());

        canal.anunciarIngreso(sala, VISITANTE);

        assertEquals(sala.ocupacion(), capturar().aviso().ocupacion().actual());
    }

    @Test
    @DisplayName("cada sala publica en su propio destino")
    void cadaSalaTieneSuDestino() {
        UUID otra = UUID.fromString("99999999-9999-9999-9999-999999999999");

        assertEquals("/tema/salas/" + otra, CanalDeSalaStomp.destinoDe(otra));
    }
}
