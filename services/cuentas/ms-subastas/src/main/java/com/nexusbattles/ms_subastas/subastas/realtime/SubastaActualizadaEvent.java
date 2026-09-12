package com.nexusbattles.ms_subastas.subastas.realtime;

import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import org.springframework.context.ApplicationEvent;

/**
 * HU-SUB-011. Se publica cuando algo del listado en vivo de una subasta
 * cambia (nueva puja, cierre, etc.) -- quien dispare el cambio real no
 * necesita saber que existe WebSocket ni STOMP, solo publicar este evento
 * con la entidad ya actualizada.
 *
 * PENDIENTE DE COORDINAR: nadie publica este evento todavia. Le
 * corresponde a MotorPujasService (Andres) dispararlo despues de guardar
 * una puja exitosa, y al job de cierre (CierreDeSubastasVencidasJob)
 * dispararlo al adjudicar/cerrar. No se edito codigo de Andres para esto
 * sin coordinar primero -- ver README, seccion de asunciones/pendientes.
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
