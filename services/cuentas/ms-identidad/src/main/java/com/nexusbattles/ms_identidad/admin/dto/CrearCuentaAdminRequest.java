package com.nexusbattles.ms_identidad.admin.dto;

import com.nexusbattles.ms_identidad.rbac.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CrearCuentaAdminRequest {

    @NotBlank
    private String nombres;

    @NotBlank
    private String apellidos;

    @Email
    @NotBlank
    private String email;

    @NotBlank
    private String password;

    @NotBlank
    private String apodo;

    private String avatar;

    @NotNull
    private Role rol;

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
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public Role getRol() { return rol; }
    public void setRol(Role rol) { this.rol = rol; }
}