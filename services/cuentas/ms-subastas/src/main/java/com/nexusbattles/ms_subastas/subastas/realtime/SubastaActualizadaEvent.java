package com.nexusbattles.ms_subastas.subastas.realtime;

import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import org.springframework.context.ApplicationEvent;

/**
 * HU-SUB-011. Se publica cuando algo del listado en vivo de una subasta
 * cambia -- quien dispare el cambio real no necesita saber que existe
 * WebSocket ni STOMP, solo publicar este evento con la entidad ya
 * actualizada.
 *
 * Disparado por PujaApplicationService (Andres), rama
 * feat/subastas-evento-realtime, tras una puja exitosa o una compra
 * inmediata -- deliberadamente NO en pujas rechazadas (perder contra otro
 * postor es el caso normal, avisar ahi llenaria el canal de ruido sin que
 * el listado realmente cambiara).
 */
public class SubastaActualizadaEvent extends ApplicationEvent {

    private final Subasta subasta;

    public SubastaActualizadaEvent(Object origen, Subasta subasta) {
        super(origen);
        this.subasta = subasta;
    }

    public Subasta getSubasta() {
        return subasta;
    }
}
