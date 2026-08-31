package com.nexusbattles.ms_cumplimiento.auditoria.dto;

public record RegistrarAuditoriaRequest(
        String tipoAccion,
        String administradorId,
        String afectado,
        String valorAnterior,
        String valorNuevo,
        String motivo,
        String ipOrigen
) {}