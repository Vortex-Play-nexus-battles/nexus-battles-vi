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
    private String biografia;
    private String preferencias;

    public static AdminUsuarioResumenResponse from(PerfilUsuario perfil) {
        AdminUsuarioResumenResponse dto = new AdminUsuarioResumenResponse();
        dto.id = perfil.getId();
        dto.apodo = perfil.getUsuario().getApodo();
        dto.email = perfil.getUsuario().getEmail();
        dto.estado = perfil.getUsuario().getEstado();
        dto.rolNombre = perfil.getUsuario().getRol().getNombre();
        dto.nombres = perfil.getNombres();
        dto.apellidos = perfil.getApellidos();
        dto.avatar = perfil.getAvatar();
        dto.biografia = perfil.getBiografia();
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
    public String getBiografia() { return biografia; }
    public String getPreferencias() { return preferencias; }
}
