package com.nexusbattles.plataforma.salaspartidas.tiemporeal;

import com.nexusbattles.plataforma.salaspartidas.dominio.AccionResuelta;
import com.nexusbattles.plataforma.salaspartidas.dominio.CanalDePartida;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Adaptador de salida: publica el avance del combate por STOMP — HU-SAL-005.
 *
 * <p>Traduce entre el puerto del dominio y el destino y el mensaje que fija el
 * AsyncAPI. No decide nada del juego: si aqui apareciera un calculo de dano o
 * de umbral, estaria en el sitio equivocado.
 *
 * <p>El destino {@code /tema/partidas/{idPartida}} es el canal
 * {@code partidaEstado} del contrato. Es un tema, no una cola por usuario: la
 * vida de cada heroe le interesa a <b>todos</b> los que estan en la partida,
 * que es lo que pide el tercer criterio del issue #31. La suscripcion a ese
 * destino la deja pasar {@link AutorizacionDeDestinos} (no es un destino de
 * sala), y el frontend ya se suscribe a el desde {@code sala-batalla.js}.
 */
@Component
class CanalDePartidaStomp implements CanalDePartida {

    /** Prefijo del canal partidaEstado. El identificador se anade al publicar. */
    static final String DESTINO_PARTIDA = "/tema/partidas/";

    private final SimpMessagingTemplate mensajeria;

    CanalDePartidaStomp(SimpMessagingTemplate mensajeria) {
        this.mensajeria = mensajeria;
    }

    @Override
    public void anunciarAccionResuelta(AccionResuelta accion) {
        mensajeria.convertAndSend(destinoDe(accion.idPartida()), AvisoDeAccionResuelta.de(accion));
    }

    static String destinoDe(UUID idPartida) {
        return DESTINO_PARTIDA + idPartida;
    }
}
