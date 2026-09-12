package com.nexusbattles.ms_subastas.subastas.realtime;

import com.nexusbattles.ms_subastas.subastas.dto.SubastaResumenResponse;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * HU-SUB-011. Escucha SubastaActualizadaEvent y lo transmite por STOMP al
 * canal compartido del listado. Un solo componente hace esta traduccion
 * (evento interno -> mensaje STOMP) para que quien publique el evento
 * (MotorPujasService, jobs) no necesite conocer SimpMessagingTemplate ni
 * el nombre del canal -- si el canal cambia de nombre despues, solo se
 * toca este archivo.
 *
 * @EventListener (no @TransactionalEventListener a proposito): el
 * contador en vivo es informativo, no critico -- no hace falta esperar a
 * que la transaccion de la puja confirme en la base de datos antes de
 * notificar. Si se prefiere esa garantia mas adelante, cambiar a
 * @TransactionalEventListener(phase = AFTER_COMMIT) es un cambio de una
 * sola anotacion aqui, no en quien publica el evento.
 */
@Component
public class SubastaRealtimePublisher {

    private static final String CANAL_LISTADO = "/topic/subastas/listado";

    private final SimpMessagingTemplate mensajeria;

    public SubastaRealtimePublisher(SimpMessagingTemplate mensajeria) {
        this.mensajeria = mensajeria;
    }

    @EventListener
    public void alActualizarSubasta(SubastaActualizadaEvent evento) {
        SubastaResumenResponse actualizado = SubastaResumenResponse.desde(evento.getSubasta());
        mensajeria.convertAndSend(CANAL_LISTADO, actualizado);
    }
}
