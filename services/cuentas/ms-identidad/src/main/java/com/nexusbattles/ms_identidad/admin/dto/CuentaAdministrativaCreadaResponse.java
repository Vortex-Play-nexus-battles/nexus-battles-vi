package com.nexusbattles.ms_identidad.admin.dto;

import com.nexusbattles.ms_identidad.auth.model.EstadoCuenta;
import com.nexusbattles.ms_identidad.auth.model.Usuario;

/**
 * Respuesta de {@code POST /api/v1/admin/cuentas} (ms-identidad-admin.yaml
 * 1.6.0). Hasta 1.5.0 se devolvia la entidad serializada entera; ahora solo lo
 * que la interfaz muestra al confirmar el alta.
 */
public record CuentaAdministrativaCreadaResponse(Long id, String uid, String apodo, String email, String rol,
                                                 String estado) {

    public static CuentaAdministrativaCreadaResponse de(Usuario usuario) {
        return new CuentaAdministrativaCreadaResponse(
                usuario.getId(),
                usuario.getPublicId() == null ? null : usuario.getPublicId().toString(),
                usuario.getApodo(),
                usuario.getEmail(),
                usuario.getRol() == null ? null : usuario.getRol().getNombre(),
                EstadoCuenta.normalizado(usuario.getEstado()));
    }
}
