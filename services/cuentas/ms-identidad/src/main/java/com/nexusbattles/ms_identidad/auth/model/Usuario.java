package com.nexusbattles.ms_identidad.auth.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El nombre es obligatorio")
    private String nombres;

    @NotBlank(message = "El apellido es obligatorio")
    private String apellidos;

    @Email(message = "Debe ser un correo válido")
    @Column(unique = true)
    private String email;

    @NotBlank
    @Size(min = 9, message = "La contraseña debe tener más de 8 caracteres")
    private String password;

    @NotBlank
    @Column(unique = true)
    private String apodo;

    private String avatar;

    private String rol = "Jugador"; // Por defecto según tu HU

    private String estado = "activo"; // Por defecto según tu HU

    // Constructor vacío obligatorio para JPA
    public Usuario() {}

    // Getters y Setters (puedes generarlos en IntelliJ con Alt + Insert -> Getter and Setter)
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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

    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}