package com.nexusbattles.ms_identidad.sanciones;

import java.util.UUID;

/**
 * {@code GET /internal/usuarios/{uid}/rol} (ms-identidad-admin.yaml 1.6.0): el
 * rol vigente y el estado de una cuenta, sin datos de contacto.
 */
public record RolDeCuentaResponse(UUID uid, String rol, String estado) {
}
