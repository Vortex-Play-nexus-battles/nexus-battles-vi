package com.nexusbattles.ms_identidad.auth.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexusbattles.ms_identidad.rbac.model.RolEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios", uniqueConstraints = {
    @UniqueConstraint(columnNames = "apodo"),
    @UniqueConstraint(columnNames = "email")
})
@Getter
@Setter
@NoArgsConstructor
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

    @Column(nullable = false)
    private int intentosFallidos = 0;

    @Column
    private LocalDateTime bloqueadoHasta;

    @Column
    private LocalDateTime suspendidoHasta;
}
