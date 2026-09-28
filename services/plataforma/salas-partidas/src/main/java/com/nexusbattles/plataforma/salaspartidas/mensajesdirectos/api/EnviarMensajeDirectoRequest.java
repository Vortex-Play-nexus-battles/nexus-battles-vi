package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.api;

/**
 * Cuerpo de {@code POST /mensajes-directos/conversaciones/{uidOtro}/mensajes}:
 * lo mismo que {@code MensajeSaliente} del AsyncAPI. Sin remitente: sale del token.
 */
public record EnviarMensajeDirectoRequest(String texto, String idCliente) {
}
