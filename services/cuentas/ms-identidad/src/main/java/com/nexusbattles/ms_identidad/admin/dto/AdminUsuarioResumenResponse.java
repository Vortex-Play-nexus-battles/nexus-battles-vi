package com.nexusbattles.ms_identidad.admin.dto;

import com.nexusbattles.ms_identidad.perfiles.model.PerfilUsuario;

public class AdminUsuarioResumenResponse {

    private Long id;
    private String apodo;
    private String email;
    private String estado;
    private String rolNombre;
    private String nombres;
    private String apellidos;
    private String avatar;
    private String preferencias;

    public static AdminUsuarioResumenResponse from(PerfilUsuario perfil) {
        AdminUsuarioResumenResponse dto = new AdminUsuarioResumenResponse();
        // RFINAL-06 — la clave de la CUENTA, no la del perfil: con este numero
        // el panel suspende, banea, reactiva, restablece la clave y cambia el
        // rol (/admin/usuarios/{usuarioId}, /rbac/usuarios/{usuarioId}/rol).
        // Hoy coinciden porque el perfil comparte la clave de su cuenta
        // (@MapsId), pero eso es un detalle del esquema, no una promesa.
        dto.id = perfil.getUsuario().getId();
        dto.apodo = perfil.getUsuario().getApodo();
        dto.email = perfil.getUsuario().getEmail();
        dto.estado = perfil.getUsuario().getEstado();
        dto.rolNombre = perfil.getUsuario().getRol().getNombre();
        dto.nombres = perfil.getNombres();
        dto.apellidos = perfil.getApellidos();
        dto.avatar = perfil.getAvatar();
        dto.preferencias = perfil.getPreferencias();
        return dto;
    }

    public Long getId() { return id; }
    public String getApodo() { return apodo; }
    public String getEmail() { return email; }
    public String getEstado() { return estado; }
    public String getRolNombre() { return rolNombre; }
    public String getNombres() { return nombres; }
    public String getApellidos() { return apellidos; }
    public String getAvatar() { return avatar; }
    public String getPreferencias() { return preferencias; }
}
