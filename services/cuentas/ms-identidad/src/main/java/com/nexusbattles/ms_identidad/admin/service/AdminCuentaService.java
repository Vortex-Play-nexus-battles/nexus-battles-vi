package com.nexusbattles.ms_identidad.admin.service;

import com.nexusbattles.ms_identidad.admin.dto.CrearCuentaAdminRequest;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.service.AuthAdminService;
import com.nexusbattles.ms_identidad.perfiles.service.PerfilUsuarioService;
import com.nexusbattles.ms_identidad.rbac.model.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminCuentaService {

    private final AuthAdminService authAdminService;
    private final PerfilUsuarioService perfilUsuarioService;

    public AdminCuentaService(AuthAdminService authAdminService,
                              PerfilUsuarioService perfilUsuarioService) {
        this.authAdminService = authAdminService;
        this.perfilUsuarioService = perfilUsuarioService;
    }

    @Transactional
    public Usuario crearCuentaAdministrativa(CrearCuentaAdminRequest datos) {

        if (datos.getRol() != Role.MODERADOR && datos.getRol() != Role.ADMINISTRADOR) {
            throw new IllegalArgumentException(
                    "Solo se pueden crear cuentas con rol MODERADOR o ADMINISTRADOR desde este endpoint.");
        }

        Usuario usuarioCreado = authAdminService.crearCuentaConRol(
                datos.getNombres(), datos.getApellidos(), datos.getEmail(),
                datos.getApodo(), datos.getAvatar(), datos.getRol()
        );

        perfilUsuarioService.crearPerfil(
                usuarioCreado, datos.getNombres(), datos.getApellidos(), datos.getAvatar()
        );

        // TODO [URGENTE - DECISIÓN DE EQUIPO, NO RESOLVER SOLO]:
        // 1. datos.getPassword() ya NO se usa — crearCuentaConRol genera su propia
        //    contraseña temporal. Confirmar si esto es lo deseado.
        // 2. estado queda "ACTIVA" (decisión dentro de crearCuentaConRol), pero el
        //    criterio de HU-USR-002 pide "inactivo hasta la primera autenticación".
        //    Ni siquiera existe un estado "INACTIVO" válido en AuthAdminServiceImpl.
        //    Conflicto real entre el contrato compartido y el criterio de la HU.
        // 3. PasswordPolicyValidator ya no se usa aquí — confirmar si sigue
        //    siendo necesario en algún otro flujo.

        return usuarioCreado;
    }
}