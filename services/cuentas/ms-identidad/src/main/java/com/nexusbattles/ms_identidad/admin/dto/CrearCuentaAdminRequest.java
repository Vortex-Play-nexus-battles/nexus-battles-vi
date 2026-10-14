package com.nexusbattles.ms_identidad.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.multipart.MultipartFile;

public class CrearCuentaAdminRequest {

    @NotBlank
    private String nombres;

    @NotBlank
    private String apellidos;

    @Email
    @NotBlank
    private String email;

    /**
     * Obsoleto y sin efecto (ms-identidad-admin.yaml 1.6.0). La cuenta nace
     * INACTIVA con una contrasena aleatoria y la persona fija la suya con el
     * codigo de activacion que le llega al correo: nadie, ni el Super
     * Administrador que la crea, conoce su contrasena. Se acepta para no
     * romper a quien lo siga mandando, y se ignora.
     */
    @Deprecated
    private String password;

    @NotBlank
    private String apodo;

    // Opcional, igual que en el registro normal.
    private MultipartFile avatar;

    @NotBlank
    private String rolNombre;

    public String getNombres() { return nombres; }
    public void setNombres(String nombres) { this.nombres = nombres; }
    public String getApellidos() { return apellidos; }
    public void setApellidos(String apellidos) { this.apellidos = apellidos; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getApodo() { return apodo; }
    public void setApodo(String apodo) { this.apodo = apodo; }
    public MultipartFile getAvatar() { return avatar; }
    public void setAvatar(MultipartFile avatar) { this.avatar = avatar; }
    public String getRolNombre() { return rolNombre; }
    public void setRolNombre(String rolNombre) { this.rolNombre = rolNombre; }
}
