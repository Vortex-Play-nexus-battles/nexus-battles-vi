package com.nexusbattles.ms_chatbot.chat.conocimiento;

import com.nexusbattles.ms_chatbot.chat.motor.model.EstadoVersion;
import com.nexusbattles.ms_chatbot.chat.motor.model.VersionBaseConocimiento;

import java.time.Instant;
import java.util.UUID;

public record VersionResponse(
    UUID id,
    int numero,
    EstadoVersion estado,
    String descripcion,
    Instant fechaCreacion,
    Instant fechaDespliegue
) {

    public static VersionResponse desde(VersionBaseConocimiento version) {
        return new VersionResponse(
            version.getId(),
            version.getNumero(),
            version.getEstado(),
            version.getDescripcion(),
            version.getFechaCreacion(),
            version.getFechaDespliegue());
    }
}
