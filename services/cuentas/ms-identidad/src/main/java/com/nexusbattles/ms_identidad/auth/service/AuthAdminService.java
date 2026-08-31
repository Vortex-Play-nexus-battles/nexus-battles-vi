package com.nexusbattles.ms_identidad.auth.service;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.rbac.model.Role;

import java.time.LocalDateTime;

public interface AuthAdminService {

    // Usado por Edwin (HU-RBAC-003)
    Role obtenerRolDeUsuario(Long usuarioId);
    long contarPorRol(Role rol);
    void actualizarRol(Long usuarioId, Role nuevoRol);

    // Usado por Sanabria (HU-USR-002)
    Usuario crearCuentaConRol(String nombres, String apellidos, String email,
                              String apodo, String avatar, Role rol);

    // Usado por Sanabria (HU-USR-003)
    void actualizarEstadoCuenta(Long usuarioId, String nuevoEstado, LocalDateTime suspendidoHasta);
    void restablecerContrasena(Long usuarioId);
    String obtenerEstadoCuenta(Long usuarioId);
}
