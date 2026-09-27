package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.api;

import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.ResumenDeConversacion;

import java.util.UUID;

/** Esquema {@code ResumenDeConversacion} de {@code contracts/openapi/salas-partidas.yaml} 1.6.x. */
public record ResumenDeConversacionResponse(
        UUID uidOtro,
        String apodoOtro,
        MensajeDirectoResponse ultimoMensaje,
        long noLeidos) {

    public static ResumenDeConversacionResponse de(ResumenDeConversacion resumen, UUID quien) {
        return new ResumenDeConversacionResponse(resumen.uidOtro(), resumen.apodoOtro(),
                MensajeDirectoResponse.de(resumen.ultimoMensaje(), quien), resumen.noLeidos());
    }
}
