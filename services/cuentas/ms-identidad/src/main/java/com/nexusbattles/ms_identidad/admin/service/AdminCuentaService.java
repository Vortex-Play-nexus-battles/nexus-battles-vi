package com.nexusbattles.ms_identidad.admin.service;

import com.nexusbattles.ms_identidad.admin.dto.CrearCuentaAdminRequest;
import com.nexusbattles.ms_identidad.auditoria.client.AuditoriaClient;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.service.AuthAdminService;
import com.nexusbattles.ms_identidad.auth.service.AvatarStorageService;
import com.nexusbattles.ms_identidad.perfiles.service.PerfilUsuarioService;
import com.nexusbattles.ms_identidad.rbac.model.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminCuentaService {

    private static final List<String> ROLES_PERMITIDOS = List.of("MODERADOR", "ADMINISTRADOR");

    private final AuthAdminService authAdminService;
    private final PerfilUsuarioService perfilUsuarioService;
    private final AuditoriaClient auditoriaClient;
    private final AvatarStorageService avatarStorageService;

    public AdminCuentaService(AuthAdminService authAdminService,
                              PerfilUsuarioService perfilUsuarioService,
                              AuditoriaClient auditoriaClient,
                              AvatarStorageService avatarStorageService) {
        this.authAdminService = authAdminService;
        this.perfilUsuarioService = perfilUsuarioService;
        this.auditoriaClient = auditoriaClient;
        this.avatarStorageService = avatarStorageService;
    }

    @Transactional
    public Usuario crearCuentaAdministrativa(CrearCuentaAdminRequest datos, String administradorId, String ipOrigen) {

        String rolSolicitado = datos.getRolNombre().trim().toUpperCase();
        if (!ROLES_PERMITIDOS.contains(rolSolicitado)) {
            throw new IllegalArgumentException(
                "Solo se pueden crear cuentas con rol MODERADOR o ADMINISTRADOR desde este endpoint.");
        }

        Role rol = Role.valueOf(rolSolicitado);

        // Mismo patrón que RegistroService: resolver el archivo a una URL
        // ANTES de crear el Usuario/PerfilUsuario.
        String urlAvatar = avatarStorageService.guardarAvatar(datos.getAvatar());

        Usuario usuarioCreado = authAdminService.crearCuentaConRol(
            datos.getNombres(), datos.getApellidos(), datos.getEmail(),
            datos.getApodo(), urlAvatar, rol
        );

        perfilUsuarioService.crearPerfil(
            usuarioCreado, datos.getNombres(), datos.getApellidos(), urlAvatar
        );

        auditoriaClient.registrar(
            "CREACION", administradorId, usuarioCreado.getApodo(),
            null, "rol=" + rolSolicitado + ", estado=INACTIVO",
            "Creación de cuenta administrativa", ipOrigen
        );

        return usuarioCreado;
    }
}
