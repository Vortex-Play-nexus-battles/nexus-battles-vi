package com.nexusbattles.ms_identidad.auth.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "tokens_credencial", uniqueConstraints = {
    @UniqueConstraint(columnNames = "token")
})
@Getter
@Setter
@NoArgsConstructor
public class TokenCredencial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    // "ACTIVACION" (cuenta creada por un admin, HU-USR-002) o
    // "RESTABLECIMIENTO" (contraseña olvidada/reseteada, HU-USR-003).
    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(nullable = false)
    private LocalDateTime fechaExpiracion;

    @Column(nullable = false)
    private boolean usado = false;

    public TokenCredencial(Usuario usuario, String token, String tipo, LocalDateTime fechaExpiracion) {
        this.usuario = usuario;
        this.token = token;
        this.tipo = tipo;
        this.fechaExpiracion = fechaExpiracion;
    }
}
