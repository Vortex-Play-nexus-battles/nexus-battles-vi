package com.nexusbattles.ms_identidad.perfiles.dto;

import com.nexusbattles.ms_identidad.perfiles.model.PerfilUsuario;

public class PerfilUsuarioResponse {

    private Long id;
    private String apodo;
    private String nombres;
    private String apellidos;
    private String avatar;
    private String biografia;
    private String preferencias;

    public static PerfilUsuarioResponse from(PerfilUsuario perfil) {
        PerfilUsuarioResponse dto = new PerfilUsuarioResponse();
        dto.id = perfil.getId();
        dto.apodo = perfil.getUsuario().getApodo();
        dto.nombres = perfil.getNombres();
        dto.apellidos = perfil.getApellidos();
        dto.avatar = perfil.getAvatar();
        dto.biografia = perfil.getBiografia();
        dto.preferencias = perfil.getPreferencias();
        return dto;
    }

    public Long getId() { return id; }
    public String getApodo() { return apodo; }
    public String getNombres() { return nombres; }
    public String getApellidos() { return apellidos; }
    public String getAvatar() { return avatar; }
    public String getBiografia() { return biografia; }
    public String getPreferencias() { return preferencias; }
}