package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.canal;

import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.EntregaDeMensajesDirectos;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.MensajeDirecto;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;

import java.util.Objects;
import java.util.UUID;

/**
 * Entrega por la cola de usuario del broker STOMP — B6.
 *
 * <p>{@code convertAndSendToUser(uid, "/cola/mensajes-directos", ...)}: el
 * broker lo resuelve a cada sesion de ese usuario que este suscrita a
 * {@code /usuario/cola/mensajes-directos}. El nombre del usuario de la sesion
 * es su {@code uid}, porque asi lo deja {@code ConversorRolesJwt} en el CONNECT.
 *
 * <p>Si alguien esta escuchando se mira en el {@link SimpUserRegistry}: tener
 * una sesion abierta no basta (puede estar en la vista de combate, que no
 * escucha esta cola); hace falta una suscripcion a ella.
 */
public class EntregaStomp implements EntregaDeMensajesDirectos {

    /** Destino de usuario, sin el prefijo {@code /usuario}. */
    public static final String COLA = "/cola/mensajes-directos";

    /** Lo que se suscribe el cliente, tal como queda en el registro. */
    static final String SUSCRIPCION = "/usuario" + COLA;

    private final SimpMessagingTemplate plantilla;
    private final SimpUserRegistry registro;

    public EntregaStomp(SimpMessagingTemplate plantilla, SimpUserRegistry registro) {
        this.plantilla = Objects.requireNonNull(plantilla);
        this.registro = Objects.requireNonNull(registro);
    }

    @Override
    public void entregar(MensajeDirecto mensaje) {
        mandar(mensaje, mensaje.destinatario());
        mandar(mensaje, mensaje.remitente());
    }

    @Override
    public void reenviarEco(MensajeDirecto mensaje) {
        mandar(mensaje, mensaje.remitente());
    }

    @Override
    public boolean estaEscuchando(UUID jugador) {
        SimpUser usuario = registro.getUser(jugador.toString());
        if (usuario == null) {
            return false;
        }
        for (SimpSession sesion : usuario.getSessions()) {
            for (SimpSubscription suscripcion : sesion.getSubscriptions()) {
                if (SUSCRIPCION.equals(suscripcion.getDestination())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void mandar(MensajeDirecto mensaje, UUID quien) {
        plantilla.convertAndSendToUser(quien.toString(), COLA, MensajeEntregadoPayload.para(mensaje, quien));
    }
}
