package com.nexusbattles.ms_identidad.admin.service;

import com.nexusbattles.ms_identidad.admin.dto.CrearCuentaAdminRequest;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.service.AuthAdminService;
import com.nexusbattles.ms_identidad.perfiles.service.PerfilUsuarioService;
import com.nexusbattles.ms_identidad.rbac.model.RolEntity;
import com.nexusbattles.ms_identidad.rbac.service.RolService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminCuentaService {

    private static final List<String> ROLES_PERMITIDOS = List.of("MODERADOR", "ADMINISTRADOR");

    private final AuthAdminService authAdminService;
    private final RolService rolService;
    private final PerfilUsuarioService perfilUsuarioService;

    public AdminCuentaService(AuthAdminService authAdminService,
                              RolService rolService,
                              PerfilUsuarioService perfilUsuarioService) {
        this.authAdminService = authAdminService;
        this.rolService = rolService;
        this.perfilUsuarioService = perfilUsuarioService;
    }

    @Transactional
    public Usuario crearCuentaAdministrativa(CrearCuentaAdminRequest datos) {

        String rolSolicitado = datos.getRolNombre().toUpperCase();
        if (!ROLES_PERMITIDOS.contains(rolSolicitado)) {
            throw new IllegalArgumentException(
                    "Solo se pueden crear cuentas con rol MODERADOR o ADMINISTRADOR desde este endpoint.");
        }

        RolEntity rolEntity = rolService.obtenerRolPorNombre(rolSolicitado);

        Usuario usuarioCreado = authAdminService.crearCuentaConRol(
                datos.getNombres(), datos.getApellidos(), datos.getEmail(),
                datos.getApodo(), datos.getAvatar(), rolEntity
        );

        perfilUsuarioService.crearPerfil(
                usuarioCreado, datos.getNombres(), datos.getApellidos(), datos.getAvatar()
        );

        // TODO [URGENTE - DECISIÓN DE EQUIPO, NO RESOLVER SOLO]:
        // 1. datos.getPassword() ya NO se usa — crearCuentaConRol genera su propia
        //    contraseña temporal. Confirmar si esto es lo deseado.
        // 2. estado queda "ACTIVA" (decisión dentro de crearCuentaConRol), pero el
        //    criterio de HU-USR-002 pide "inactivo hasta la primera autenticación".
        //    Conflicto real entre el contrato compartido y el criterio de la HU.

        return usuarioCreado;
    }
}