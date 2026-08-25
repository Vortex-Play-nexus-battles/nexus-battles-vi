package com.nexusbattles.ms_identidad.rbac.service;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.rbac.model.Role;
import org.springframework.stereotype.Service;

@Service
public class RoleAssignmentService {

    private final UsuarioRepository usuarioRepository;

    public RoleAssignmentService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    public Usuario cambiarRol(
            Long idSolicitante,
            Long idUsuarioObjetivo,
            Role nuevoRol) {

        Usuario solicitante = usuarioRepository.findById(idSolicitante)
                .orElseThrow(() ->
                        new RuntimeException("El usuario solicitante no existe."));

        Usuario usuarioObjetivo = usuarioRepository.findById(idUsuarioObjetivo)
                .orElseThrow(() ->
                        new RuntimeException("El usuario objetivo no existe."));

        // Solo un Super Administrador puede modificar roles.
        if (!Role.SUPER_ADMINISTRADOR.getDisplayName()
                .equals(solicitante.getRol())) {

            throw new RuntimeException(
                    "Solo un Super Administrador puede modificar roles.");
        }

        boolean objetivoEsSuperAdministrador =
                Role.SUPER_ADMINISTRADOR.getDisplayName()
                        .equals(usuarioObjetivo.getRol());

        boolean dejaraDeSerSuperAdministrador =
                nuevoRol != Role.SUPER_ADMINISTRADOR;

        long cantidadSuperAdministradores =
                usuarioRepository.countByRol(
                        Role.SUPER_ADMINISTRADOR.getDisplayName());

        // Impide dejar el sistema sin ningún Super Administrador.
        if (objetivoEsSuperAdministrador
                && dejaraDeSerSuperAdministrador
                && cantidadSuperAdministradores <= 1) {

            throw new RuntimeException(
                    "No se puede modificar el rol del último Super Administrador.");
        }

        String rolAnterior = usuarioObjetivo.getRol();

        usuarioObjetivo.setRol(nuevoRol.getDisplayName());

        Usuario usuarioActualizado =
                usuarioRepository.save(usuarioObjetivo);

        // TODO HU-RBAC-003:
        // Invalidar las sesiones activas del usuario afectado
        // cuando el módulo de sesiones esté disponible.

        // TODO HU-RBAC-003:
        // Registrar en auditoría el solicitante, usuario afectado,
        // rol anterior y rol nuevo cuando el módulo esté disponible.

        return usuarioActualizado;
    }
}
