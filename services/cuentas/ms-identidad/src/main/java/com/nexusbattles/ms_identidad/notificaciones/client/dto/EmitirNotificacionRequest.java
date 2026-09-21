package com.nexusbattles.ms_identidad.notificaciones.client.dto;

import java.time.Instant;

/**
 * Cuerpo del alta de un aviso en el microservicio de notificaciones
 * (POST /api/v1/internal/notifications). Coincide con el contrato
 * EmitirNotificacionRequest que publica ese servicio.
 */
public record EmitirNotificacionRequest(
    String usuarioId,
    String id,
    String tipo,
    String titulo,
    String cuerpo,
    Instant creadaEn
) {}
