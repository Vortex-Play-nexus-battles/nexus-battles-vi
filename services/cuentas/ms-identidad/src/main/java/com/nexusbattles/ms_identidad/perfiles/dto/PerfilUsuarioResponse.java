package com.nexusbattles.ms_identidad.perfiles.dto;

import com.nexusbattles.ms_identidad.perfiles.model.PerfilUsuario;

public class PerfilUsuarioResponse {

    private Long id;
    private String apodo;
    /**
     * RFINAL-05 (ms-identidad-perfiles.yaml 1.4.0) — el correo de la cuenta,
     * para el portal de privacidad (RF-PRV-004). Las rutas que devuelven este
     * DTO solo responden al dueno del perfil o a la administracion.
     */
    private String email;
    private String nombres;
    private String apellidos;
    private String avatar;
    private String preferencias;

    public static PerfilUsuarioResponse from(PerfilUsuario perfil) {
        PerfilUsuarioResponse dto = new PerfilUsuarioResponse();
        dto.id = perfil.getId();
        dto.apodo = perfil.getUsuario().getApodo();
        dto.email = perfil.getUsuario().getEmail();
        dto.nombres = perfil.getNombres();
        dto.apellidos = perfil.getApellidos();
        dto.avatar = perfil.getAvatar();
        dto.preferencias = perfil.getPreferencias();
        return dto;
    }

    public Long getId() { return id; }
    public String getApodo() { return apodo; }
    public String getEmail() { return email; }
    public String getNombres() { return nombres; }
    public String getApellidos() { return apellidos; }
    public String getAvatar() { return avatar; }
    public String getPreferencias() { return preferencias; }
}
