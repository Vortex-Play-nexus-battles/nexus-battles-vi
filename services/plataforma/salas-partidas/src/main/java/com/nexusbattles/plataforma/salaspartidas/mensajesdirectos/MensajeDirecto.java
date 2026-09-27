package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Un mensaje privado ya aceptado — B6 (feedback del profesor).
 *
 * <p>Paso las mismas comprobaciones que el chat (sancion y lista negra,
 * ahora con contexto {@code MENSAJE_PRIVADO}) mas las propias de un mensaje
 * entre dos: el destinatario existe y esta activo, no es uno mismo y el
 * remitente no va demasiado rapido. Lo que no llega hasta aqui no existe.
 *
 * @param id                identificador del servidor
 * @param conversacion      la de los dos participantes
 * @param remitente         uid de quien escribe; SIEMPRE el del token
 * @param apodoRemitente    su apodo al escribir, para pintarlo sin preguntar a nadie
 * @param destinatario      uid de quien recibe
 * @param apodoDestinatario su apodo al recibir, tal como lo dio ms-identidad al
 *                          comprobar que existe; es lo que permite listar las
 *                          conversaciones con el nombre del otro sin una
 *                          llamada por fila
 * @param texto             ya recortado, de 1 a 500 caracteres
 * @param enviadoEn         momento en que el servidor lo acepto
 * @param leidoEn           cuando el destinatario lo leyo, o {@code null}
 * @param idCliente         el que puso el cliente para reconocer el eco, o {@code null}
 */
public record MensajeDirecto(
        UUID id,
        Conversacion conversacion,
        UUID remitente,
        String apodoRemitente,
        UUID destinatario,
        String apodoDestinatario,
        String texto,
        Instant enviadoEn,
        Instant leidoEn,
        String idCliente) {

    public MensajeDirecto {
        Objects.requireNonNull(id, "Un mensaje sin identificador no se puede guardar.");
        Objects.requireNonNull(conversacion, "Un mensaje privado es de una conversacion.");
        Objects.requireNonNull(remitente, "Un mensaje privado tiene remitente.");
        Objects.requireNonNull(destinatario, "Un mensaje privado tiene destinatario.");
        Objects.requireNonNull(texto, "Un mensaje sin texto no es un mensaje.");
        Objects.requireNonNull(enviadoEn, "Hace falta el momento del envio.");
        if (!conversacion.incluye(remitente) || !conversacion.incluye(destinatario)) {
            throw new IllegalArgumentException("Remitente y destinatario son los dos de la conversacion.");
        }
    }

    /**
     * Si, para quien lo mira, este mensaje ya no esta pendiente de leer.
     *
     * <p>Lo que uno escribio cuenta como leido para el: el acuse de lectura
     * del otro no se publica, porque ningun requisito lo pide y decirle a
     * alguien cuando leyo otra persona su mensaje es un dato de esa persona.
     */
    public boolean leidoPara(UUID quienMira) {
        return remitente.equals(quienMira) || leidoEn != null;
    }

    /**
     * El {@code idCliente} solo lo ve su autor: es suyo, y al otro no le sirve
     * para nada.
     */
    public String idClientePara(UUID quienMira) {
        return remitente.equals(quienMira) ? idCliente : null;
    }

    /** El apodo del otro participante, visto desde uno de los dos. */
    public String apodoDelOtro(UUID quienMira) {
        return remitente.equals(quienMira) ? apodoDestinatario : apodoRemitente;
    }
}
