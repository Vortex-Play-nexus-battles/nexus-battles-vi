package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.canal;

import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.MensajeDirecto;

import java.time.Instant;
import java.util.UUID;

/**
 * Mensaje {@code MensajeEntregado} de {@code contracts/websocket/mensajes-directos.yaml}.
 *
 * <p>Se arma para cada destinatario de la entrega: el {@code idCliente} solo
 * viaja en el eco a su autor (ver {@link MensajeDirecto#idClientePara}).
 */
public record MensajeEntregadoPayload(
        String tipo,
        UUID id,
        String conversacion,
        UUID remitente,
        String apodoRemitente,
        UUID destinatario,
        String texto,
        Instant fecha,
        String idCliente) {

    public static final String TIPO = "MENSAJE";

    /** El mensaje tal como lo recibe {@code quien}. */
    public static MensajeEntregadoPayload para(MensajeDirecto mensaje, UUID quien) {
        return new MensajeEntregadoPayload(TIPO, mensaje.id(), mensaje.conversacion().clave(),
                mensaje.remitente(), mensaje.apodoRemitente(), mensaje.destinatario(), mensaje.texto(),
                mensaje.enviadoEn(), mensaje.idClientePara(quien));
    }
}
