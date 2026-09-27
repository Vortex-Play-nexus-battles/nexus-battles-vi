package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.canal;

/**
 * Cuerpo de {@code MensajeSaliente} en {@code contracts/websocket/mensajes-directos.yaml}.
 *
 * <p>No lleva remitente a proposito: sale del token. Si viniera aqui,
 * cualquiera podria escribir en nombre de otro cambiando un campo.
 */
public record MensajeSalienteRequest(String texto, String idCliente) {
}
