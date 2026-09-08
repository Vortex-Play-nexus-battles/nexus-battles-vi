package com.nexusbattles.ms_identidad.auditoria.client.dto;

public record RegistrarAuditoriaRequest(
    String tipoAccion,
    String administradorId,
    String afectado,
    String valorAnterior,
    String valorNuevo,
    String motivo,
    String ipOrigen
) {}
