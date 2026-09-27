package com.nexusbattles.ms_identidad.sanciones;

import java.util.UUID;

/**
 * Datos de contacto de una cuenta para otro servicio que necesita escribirle
 * (ms-identidad-admin.yaml, {@code GET /internal/usuarios/{uid}/contacto}).
 * Asi los productores de correo no guardan copias del correo de nadie en sus
 * propias bases (regla 7 y minimizacion de datos personales).
 */
public record ContactoResponse(UUID uid, String email, String apodo, String estado) {
}
