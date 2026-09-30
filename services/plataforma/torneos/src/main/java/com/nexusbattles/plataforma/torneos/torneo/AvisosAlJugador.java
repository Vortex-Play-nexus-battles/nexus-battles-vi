package com.nexusbattles.plataforma.torneos.torneo;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Puerto hacia los canales con los que torneos le cuenta algo a un jugador:
 * su bandeja de notificaciones ({@code POST /internal/notifications},
 * notificaciones.yaml) y, si esta configurado, su correo
 * ({@code POST /correos/torneo}, correo.yaml 1.5.0, con el contacto que da
 * ms-identidad por {@code uid}).
 */
public interface AvisosAlJugador {

    /** Resultado de intentar un correo. */
    enum Correo { ENVIADO, SIN_CONTACTO }

    /**
     * Deja el aviso en la bandeja. Idempotente por {@code id}: si ya existia,
     * cuenta como entregado.
     *
     * @throws FalloDeIntegracion si no se pudo
     */
    void notificar(UUID destinatario, String id, String titulo, String cuerpo, OffsetDateTime creadaEn);

    /** Si hay correo configurado; sin el, torneos no crea operaciones de correo. */
    boolean correoConfigurado();

    /**
     * Manda el correo del hito. Idempotente por {@code claveIdempotente}.
     *
     * @return SIN_CONTACTO si ms-identidad no tiene cuenta para ese uid
     * @throws FalloDeIntegracion si no se pudo
     */
    Correo enviarCorreo(UUID destinatario, UUID torneoId, String asunto, String mensaje, String claveIdempotente);
}
