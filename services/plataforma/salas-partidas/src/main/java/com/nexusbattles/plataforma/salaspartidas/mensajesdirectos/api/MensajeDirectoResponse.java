package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.api;

import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.MensajeDirecto;

import java.time.Instant;
import java.util.UUID;

/**
 * Esquema {@code MensajeDirecto} de {@code contracts/openapi/salas-partidas.yaml} 1.6.x,
 * visto por {@code quien} (ver {@link MensajeDirecto#leidoPara} y
 * {@link MensajeDirecto#idClientePara}).
 */
public record MensajeDirectoResponse(
        UUID id,
        String conversacion,
        UUID remitente,
        String apodoRemitente,
        UUID destinatario,
        String texto,
        Instant fecha,
        boolean leido,
        String idCliente) {

    public static MensajeDirectoResponse de(MensajeDirecto mensaje, UUID quien) {
        return new MensajeDirectoResponse(mensaje.id(), mensaje.conversacion().clave(), mensaje.remitente(),
                mensaje.apodoRemitente(), mensaje.destinatario(), mensaje.texto(), mensaje.enviadoEn(),
                mensaje.leidoPara(quien), mensaje.idClientePara(quien));
    }
}
