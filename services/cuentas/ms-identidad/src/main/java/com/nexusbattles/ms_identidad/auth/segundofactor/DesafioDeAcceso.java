package com.nexusbattles.ms_identidad.auth.segundofactor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * El desafio del login en dos pasos (tabla {@code desafios_acceso}, V5).
 *
 * <p>Lo emite {@code POST /auth/login} cuando la contrasena es correcta pero
 * falta el segundo factor. Es lo que prueba, en el segundo paso, que la
 * contrasena ya se dio: por eso vive poco, sirve una vez y caduca si la cuenta
 * cambia de version de token (un cambio de contrasena o de rol entre los dos
 * pasos).
 *
 * <p><b>Opaco y guardado resumido.</b> No es un JWT: un JWT firmado con la
 * clave de la plataforma lo aceptaria como credencial cualquier servicio que
 * solo pida «autenticado», y el desafio no debe abrir nada. Es un valor al
 * azar de 256 bits; la base guarda su SHA-256 (con esa entropia no hace falta
 * BCrypt), asi que una copia de la base no permite canjear ninguno.
 */
@Entity
@Table(name = "desafios_acceso")
public class DesafioDeAcceso {

    /** Para que sirve el desafio. */
    public enum Proposito {
        /** La cuenta tiene segundo factor: falta el codigo. */
        VERIFICAR,
        /** El rol lo exige y la cuenta aun no lo tiene: falta enrolarse. */
        ENROLAR
    }

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Proposito proposito;

    @Column(name = "version_token", nullable = false)
    private int versionToken;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;

    @Column(name = "usado_en")
    private LocalDateTime usadoEn;

    protected DesafioDeAcceso() {
    }

    public DesafioDeAcceso(Long usuarioId, String tokenHash, Proposito proposito, int versionToken,
                           LocalDateTime creadoEn, LocalDateTime expiraEn) {
        this.id = UUID.randomUUID();
        this.usuarioId = usuarioId;
        this.tokenHash = tokenHash;
        this.proposito = proposito;
        this.versionToken = versionToken;
        this.creadoEn = creadoEn;
        this.expiraEn = expiraEn;
    }

    /** Vigente: sin usar y sin caducar. */
    public boolean vigente(LocalDateTime ahora) {
        return usadoEn == null && ahora.isBefore(expiraEn);
    }

    public void usar(LocalDateTime ahora) {
        this.usadoEn = ahora;
    }

    public UUID getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Proposito getProposito() {
        return proposito;
    }

    public int getVersionToken() {
        return versionToken;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public LocalDateTime getExpiraEn() {
        return expiraEn;
    }

    public LocalDateTime getUsadoEn() {
        return usadoEn;
    }
}
