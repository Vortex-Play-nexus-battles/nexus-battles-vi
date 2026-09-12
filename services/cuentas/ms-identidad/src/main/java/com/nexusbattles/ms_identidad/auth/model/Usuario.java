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

import java.util.UUID;

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

    /**
     * Identificador publico y estable del usuario, para que otros servicios lo
     * referencien sin depender del apodo (que es mutable: se cambia desde
     * PerfilUsuarioService y desde la edicion de administracion) ni del `id`
     * interno (secuencial y por tanto enumerable desde fuera).
     *
     * <p>Viaja en el JWT como claim `uid`. ms-subastas lo necesita porque su
     * dominio referencia jugadores por UUID y una puja retiene creditos: si el
     * identificador cambiara, las pujas quedarian huerfanas y quien tomara el
     * apodo liberado heredaria sus creditos reservados.
     *
     * <p>Es nullable en el mapeo a proposito. El servicio corre con
     * `ddl-auto=update` y no tiene Flyway todavia (R8 pendiente), asi que la
     * columna no puede nacer NOT NULL sobre una tabla con filas existentes.
     * Los usuarios nuevos la reciben en {@link #asignarIdentificadorPublico()};
     * a los anteriores los rellena RellenoDeIdentificadorPublico al arrancar.
     * Cuando se monte Flyway, endurecer a NOT NULL.
     */
    @Column(name = "public_id", unique = true)
    private UUID publicId;

    /**
     * Se ejecuta antes del primer INSERT. Nunca sobreescribe uno existente,
     * para que reasignar una entidad cargada no cambie su identificador.
     */
    @PrePersist
    void asignarIdentificadorPublico() {
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
    }

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

    // Se incrementa cada vez que cambia el rol del usuario (HU-RBAC-003).
    // Permite invalidar tokens JWT ya emitidos con el rol anterior, sin
    // necesitar una lista negra de tokens: si la versión del token no
    // coincide con esta, se rechaza aunque la firma siga siendo válida.
    @Column(nullable = false)
    private int versionToken = 0;
}
