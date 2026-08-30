package com.nexusbattles.ms_identidad.perfiles.dto;

import jakarta.validation.constraints.NotBlank;

public class ActualizarPerfilRequest {

    @NotBlank
    private String nombres;

    @NotBlank
    private String apellidos;

    private String avatar;

    private String biografia;

    private String preferencias;

    // Opcional: solo si el usuario quiere cambiar su apodo (dispara la validación de lista negra)
    private String apodo;

    public String getNombres() { return nombres; }
    public void setNombres(String nombres) { this.nombres = nombres; }

    public String getApellidos() { return apellidos; }
    public void setApellidos(String apellidos) { this.apellidos = apellidos; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    public String getBiografia() { return biografia; }
    public void setBiografia(String biografia) { this.biografia = biografia; }

    public String getPreferencias() { return preferencias; }
    public void setPreferencias(String preferencias) { this.preferencias = preferencias; }

    public String getApodo() { return apodo; }
    public void setApodo(String apodo) { this.apodo = apodo; }
}