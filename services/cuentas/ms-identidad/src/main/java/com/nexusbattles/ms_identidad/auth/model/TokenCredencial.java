package com.nexusbattles.ms_identidad.auth.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Un codigo de un solo uso enviado al correo de una cuenta: verificacion del
 * correo (autorregistro), activacion (cuenta creada por un Super
 * Administrador) o restablecimiento de contrasena.
 *
 * <p><b>Desde B1 el codigo no se guarda.</b> Solo su resumen BCrypt
 * ({@code codigoHash}), igual que una contrasena: quien lea la base no puede
 * canjear nada. {@code token} es la columna heredada que lo guardaba en claro;
 * queda nula en las filas nuevas y V3 la vacio en las anteriores.
 *
 * <p>Ciclo de vida: nace vigente; muere usado ({@code usado}), anulado
 * ({@code anuladoEn}: lo sustituyo uno nuevo o agoto sus intentos) o caducado
 * ({@code fechaExpiracion}). Solo el ultimo emitido de su familia puede estar
 * vigente (ver {@code CodigosDeCorreo}).
 */
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

    /** Heredado: el codigo en claro de antes de B1. Nulo en toda fila nueva. */
    @Column(unique = true, length = 64)
    private String token;

    // "VERIFICACION" (autorregistro, B1), "ACTIVACION" (cuenta creada por un
    // admin, HU-USR-002) o "RESTABLECIMIENTO" (contraseña olvidada, HU-COR-003).
    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(nullable = false)
    private LocalDateTime fechaExpiracion;

    @Column(nullable = false)
    private boolean usado = false;

    /** Resumen BCrypt del codigo normalizado. */
    @Column(name = "codigo_hash", length = 100)
    private String codigoHash;

    /** Intentos fallidos contra ESTE codigo; al llegar al maximo se anula. */
    @Column(name = "intentos_fallidos", nullable = false)
    private int intentosFallidos = 0;

    @Column(name = "anulado_en")
    private LocalDateTime anuladoEn;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @Column(name = "usado_en")
    private LocalDateTime usadoEn;

    public TokenCredencial(Usuario usuario, String tipo, String codigoHash,
                           LocalDateTime creadoEn, LocalDateTime fechaExpiracion) {
        this.usuario = usuario;
        this.tipo = tipo;
        this.codigoHash = codigoHash;
        this.creadoEn = creadoEn;
        this.fechaExpiracion = fechaExpiracion;
    }
}
