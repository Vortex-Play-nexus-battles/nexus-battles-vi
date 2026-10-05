package com.nexusbattles.ms_subastas.notificaciones;

import java.util.Optional;
import java.util.UUID;

/**
 * El correo y el apodo de una cuenta, a quien escribirle
 * ({@code GET /api/v1/internal/usuarios/{uid}/contacto} de
 * {@code ms-identidad-admin.yaml}, con credencial de servicio).
 *
 * <p>Se pide en el momento de enviar y no se guarda (regla 7 y minimizacion de
 * datos personales): ms-subastas no tiene copia del correo de nadie.
 */
public interface ContactoClient {

    /**
     * @return el contacto; vacio si quien implemente el puerto sabe con certeza
     *         que no hay a quien escribirle. El adaptador HTTP no devuelve
     *         vacio ante un 404: ver {@code ContactoClientHttp}.
     * @throws CorreoNoDisponibleException si ms-identidad no respondio, no
     *         acepto la credencial o respondio 404: se reintenta mas tarde
     */
    Optional<Contacto> contactoDe(UUID uid);

    /** @param estado ACTIVO, SUSPENDIDO, BANEADO, PENDIENTE_VERIFICACION... tal cual lo da ms-identidad */
    record Contacto(UUID uid, String email, String apodo, String estado) {
    }
}
