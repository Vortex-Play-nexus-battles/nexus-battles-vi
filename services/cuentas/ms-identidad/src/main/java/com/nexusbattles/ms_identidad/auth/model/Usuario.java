package com.nexusbattles.ms_identidad.auth.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexusbattles.ms_identidad.rbac.model.RolEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "usuarios", uniqueConstraints = {
        @UniqueConstraint(columnNames = "apodo"),
        @UniqueConstraint(columnNames = "email")
})
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 50)
    private String apodo;

    @Email
    @NotBlank
    @Column(nullable = false, length = 100)
    private String email;

    @NotBlank
    @Size(min = 9, message = "La contraseña debe tener más de 8 caracteres")
    @Column(nullable = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @Column(nullable = false)
    private String estado = "ACTIVO";

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "rol_id", nullable = false)
    private RolEntity rol;

    public Usuario() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getApodo() { return apodo; }
    public void setApodo(String apodo) { this.apodo = apodo; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public RolEntity getRol() { return rol; }
    public void setRol(RolEntity rol) { this.rol = rol; }
}
