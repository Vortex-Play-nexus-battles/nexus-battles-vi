package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.api;

import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.EstadoDeConversacion;

import java.util.UUID;

/** Esquema {@code EstadoDeConversacion} de {@code salas-partidas.yaml} 1.8.0 (D-40). */
public record EstadoDeConversacionResponse(UUID uidOtro, String estado) {

    public static EstadoDeConversacionResponse de(UUID uidOtro, EstadoDeConversacion estado) {
        return new EstadoDeConversacionResponse(uidOtro, estado.name());
    }
}
