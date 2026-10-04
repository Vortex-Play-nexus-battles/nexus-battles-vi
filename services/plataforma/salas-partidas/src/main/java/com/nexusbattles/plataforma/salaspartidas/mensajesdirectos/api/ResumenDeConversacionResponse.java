package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.api;

import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.ResumenDeConversacion;

import java.util.UUID;

/**
 * Esquema {@code ResumenDeConversacion} de {@code contracts/openapi/salas-partidas.yaml}
 * 1.8.0: desde D-40 dice tambien si se puede escribir ({@code estado}).
 */
public record ResumenDeConversacionResponse(
        UUID uidOtro,
        String apodoOtro,
        MensajeDirectoResponse ultimoMensaje,
        long noLeidos,
        String estado) {

    public static ResumenDeConversacionResponse de(ResumenDeConversacion resumen, UUID quien) {
        return new ResumenDeConversacionResponse(resumen.uidOtro(), resumen.apodoOtro(),
                MensajeDirectoResponse.de(resumen.ultimoMensaje(), quien), resumen.noLeidos(),
                resumen.estado().name());
    }
}
