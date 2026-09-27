package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Almacen de los mensajes privados — puerto de salida (B6).
 *
 * <p>Tabla propia, {@code mensajes_directos} (V13), y no la del chat: ver la
 * migracion para el porque.
 */
public interface RepositorioDeMensajesDirectos {

    /**
     * Guarda el mensaje. Idempotente por {@code (remitente, idCliente)}: si ya
     * habia uno con ese par —un reintento del mismo envio, o dos que se
     * cruzaron—, no guarda otro y devuelve el que ya estaba.
     */
    Guardado guardar(MensajeDirecto mensaje);

    /** El mensaje de ese remitente con ese {@code idCliente}, si lo hay. */
    Optional<MensajeDirecto> buscarPorIdCliente(UUID remitente, String idCliente);

    /**
     * Una pagina del historial, del mas antiguo al mas reciente.
     *
     * @param antesDe solo los anteriores a este instante; {@code null} = los ultimos
     * @param limite  cuantos como mucho
     */
    List<MensajeDirecto> historial(Conversacion conversacion, Instant antesDe, int limite);

    /** El ultimo mensaje de cada conversacion en la que participa ese jugador. */
    List<MensajeDirecto> ultimosPorConversacion(UUID participante);

    /** No leidos de ese destinatario, contados por quien los escribio. */
    Map<UUID, Long> noLeidosPorRemitente(UUID destinatario);

    /** Cuantos de ese remitente siguen sin leer por ese destinatario. */
    long noLeidos(UUID destinatario, UUID remitente);

    /**
     * El mas antiguo de ese remitente que ese destinatario no ha leido: el que
     * abre la racha de no leidos. Vacio si los ha leido todos.
     */
    Optional<MensajeDirecto> primerNoLeido(UUID destinatario, UUID remitente);

    /**
     * Marca como leidos los de ese remitente a ese destinatario.
     *
     * @return cuantos cambiaron; 0 si ya estaban leidos (idempotente)
     */
    int marcarLeidos(UUID destinatario, UUID remitente, Instant cuando);

    /**
     * @param mensaje el guardado (el nuevo, o el que ya existia)
     * @param nuevo   {@code false} si era un reintento y no se guardo nada
     */
    record Guardado(MensajeDirecto mensaje, boolean nuevo) {
    }
}
