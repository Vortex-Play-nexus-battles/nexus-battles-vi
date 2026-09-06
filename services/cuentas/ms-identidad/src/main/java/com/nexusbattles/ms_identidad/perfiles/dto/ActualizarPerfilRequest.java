package com.nexusbattles.ms_identidad.perfiles.dto;

import jakarta.validation.constraints.NotBlank;
import org.springframework.web.multipart.MultipartFile;

public class ActualizarPerfilRequest {

    @NotBlank
    private String nombres;

    @NotBlank
    private String apellidos;

    // Opcional: solo si el usuario sube una foto nueva. Si viene vacio/null,
    // se conserva el avatar que ya tenia (ver PerfilUsuarioService).
    private MultipartFile avatar;

    private String preferencias;

    // Opcional: solo si el usuario quiere cambiar su apodo (dispara la validación de lista negra)
    private String apodo;

    public String getNombres() { return nombres; }
    public void setNombres(String nombres) { this.nombres = nombres; }

    public String getApellidos() { return apellidos; }
    public void setApellidos(String apellidos) { this.apellidos = apellidos; }

    public MultipartFile getAvatar() { return avatar; }
    public void setAvatar(MultipartFile avatar) { this.avatar = avatar; }

    public String getPreferencias() { return preferencias; }
    public void setPreferencias(String preferencias) { this.preferencias = preferencias; }

    public String getApodo() { return apodo; }
    public void setApodo(String apodo) { this.apodo = apodo; }
}
