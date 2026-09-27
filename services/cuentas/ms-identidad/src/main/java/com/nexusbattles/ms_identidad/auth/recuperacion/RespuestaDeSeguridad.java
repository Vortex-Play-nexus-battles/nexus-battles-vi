package com.nexusbattles.ms_identidad.auth.recuperacion;

import java.util.UUID;

/** {@code RespuestaDeSeguridad} de ms-identidad-auth.yaml: una respuesta por {@code preguntaId}. */
public record RespuestaDeSeguridad(UUID preguntaId, String respuesta) {

    @Override
    public String toString() {
        return "RespuestaDeSeguridad[preguntaId=" + preguntaId + ", respuesta=********]";
    }
}
