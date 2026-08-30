package com.nexusbattles.ms_identidad.admin.service;

import com.nexusbattles.ms_identidad.admin.dto.CrearCuentaAdminRequest;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.service.AuthAdminService;
import com.nexusbattles.ms_identidad.perfiles.service.PerfilUsuarioService;
import com.nexusbattles.ms_identidad.rbac.model.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class AdminCuentaService {

    private static final List<String> ROLES_PERMITIDOS =
            List.of("MODERADOR", "ADMINISTRADOR");

    private final AuthAdminService authAdminService;
    private final PerfilUsuarioService perfilUsuarioService;

    public AdminCuentaService(AuthAdminService authAdminService,
                              PerfilUsuarioService perfilUsuarioService) {
        this.authAdminService = authAdminService;
        this.perfilUsuarioService = perfilUsuarioService;
    }

    @Transactional
    public Usuario crearCuentaAdministrativa(CrearCuentaAdminRequest datos) {

        String rolSolicitado = datos.getRolNombre()
                .trim()
                .toUpperCase(Locale.ROOT);

        if (!ROLES_PERMITIDOS.contains(rolSolicitado)) {
            throw new IllegalArgumentException(
                    "Solo se pueden crear cuentas con rol MODERADOR o ADMINISTRADOR desde este endpoint."
            );
        }

        Role rol = Role.valueOf(rolSolicitado);

        Usuario usuarioCreado = authAdminService.crearCuentaConRol(
                datos.getNombres(),
                datos.getApellidos(),
                datos.getEmail(),
                datos.getApodo(),
                datos.getAvatar(),
                rol
        );

        perfilUsuarioService.crearPerfil(
                usuarioCreado,
                datos.getNombres(),
                datos.getApellidos(),
                datos.getAvatar()
        );

        // TODO: confirmar con el equipo el envío de contraseña temporal
        // y el estado inicial INACTIVO requerido por la HU-USR-002.

        return usuarioCreado;
    }
}