package com.nexusbattles.plataforma.salaspartidas.tiemporeal;

import com.nexusbattles.plataforma.salaspartidas.dominio.CanalDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Adaptador de salida: publica los hechos de una sala por STOMP.
 *
 * <p>Su unico trabajo es traducir entre el puerto del dominio y el destino y el
 * mensaje que fija el AsyncAPI. No decide nada: si aqui apareciera una regla
 * del juego, estaria en el sitio equivocado.
 *
 * <p>El destino {@code /tema/salas/{idSala}} es el canal {@code salaEstado} del
 * contrato. Publicar en un tema y no en una cola por usuario es deliberado: el
 * hecho le interesa a <b>todos</b> los que estan en la sala, que es exactamente
 * lo que pide el tercer criterio de HU-SAL-002.
 */
@Component
class CanalDeSalaStomp implements CanalDeSala {

    /** Prefijo del canal salaEstado. El identificador se anade al publicar. */
    static final String DESTINO_SALA = "/tema/salas/";

    private final SimpMessagingTemplate mensajeria;

    CanalDeSalaStomp(SimpMessagingTemplate mensajeria) {
        this.mensajeria = mensajeria;
    }

    @Override
    public void anunciarIngreso(Sala sala, UUID idJugador) {
        mensajeria.convertAndSend(destinoDe(sala.id()), AvisoDeIngreso.de(sala, idJugador));
    }

    static String destinoDe(UUID idSala) {
        return DESTINO_SALA + idSala;
    }
}
