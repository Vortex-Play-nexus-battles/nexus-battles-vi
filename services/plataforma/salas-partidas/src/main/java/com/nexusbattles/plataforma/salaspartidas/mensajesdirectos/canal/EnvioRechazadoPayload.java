package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.canal;

import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.MensajeDirectoRechazado;

/**
 * Mensaje {@code EnvioRechazado} de {@code contracts/websocket/mensajes-directos.yaml}:
 * {@code {tipo: RECHAZO, motivo, idCliente}} por la cola privada de quien
 * escribio. El texto para la persona lo pone la interfaz a partir del motivo.
 */
public record EnvioRechazadoPayload(String tipo, String motivo, String idCliente) {

    public static final String TIPO = "RECHAZO";

    public static EnvioRechazadoPayload de(MensajeDirectoRechazado rechazo) {
        return new EnvioRechazadoPayload(TIPO, rechazo.motivo().name(), rechazo.idCliente());
    }
}
