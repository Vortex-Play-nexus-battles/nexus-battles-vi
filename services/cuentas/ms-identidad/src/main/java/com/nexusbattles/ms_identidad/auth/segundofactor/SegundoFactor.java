package com.nexusbattles.ms_identidad.auth.segundofactor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * El segundo factor TOTP de una cuenta (tabla {@code segundo_factor}, V5).
 *
 * <p>Una fila por cuenta. Nace con {@code activo = false} al pedir el
 * enrolamiento y pasa a {@code true} cuando un codigo de la aplicacion
 * confirma que el secreto quedo bien guardado; solo entonces el login lo pide.
 * El secreto va cifrado ({@link CifradoDeSecretos}), nunca en claro.
 *
 * <p>{@code ultimoPasoUsado} es el ultimo paso TOTP aceptado: lo que impide
 * reutilizar un codigo dentro de su ventana. Se escribe con un UPDATE
 * condicionado ({@link SegundoFactorRepository#registrarPaso}), no leyendo y
 * guardando: dos peticiones con el mismo codigo a la vez no pasan las dos.
 */
@Entity
@Table(name = "segundo_factor")
public class SegundoFactor {

    @Id
    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "secreto_cifrado", nullable = false, length = 255)
    private String secretoCifrado;

    @Column(nullable = false)
    private boolean activo;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "activado_en")
    private LocalDateTime activadoEn;

    @Column(name = "ultimo_paso_usado")
    private Long ultimoPasoUsado;

    protected SegundoFactor() {
    }

    /** Un enrolamiento nuevo, todavia sin confirmar. */
    public SegundoFactor(Long usuarioId, String secretoCifrado, LocalDateTime creadoEn) {
        this.usuarioId = usuarioId;
        this.secretoCifrado = secretoCifrado;
        this.creadoEn = creadoEn;
        this.activo = false;
    }

    /** Sustituye un enrolamiento pendiente por otro secreto (el anterior deja de valer). */
    public void reemplazarPendiente(String secretoCifrado, LocalDateTime ahora) {
        if (activo) {
            throw new IllegalStateException("Un segundo factor activo no se reemplaza: se desactiva primero.");
        }
        this.secretoCifrado = secretoCifrado;
        this.creadoEn = ahora;
        this.ultimoPasoUsado = null;
    }

    /** Confirmado con el codigo del paso {@code paso}: el login lo pedira desde ahora. */
    public void activar(LocalDateTime ahora, long paso) {
        this.activo = true;
        this.activadoEn = ahora;
        this.ultimoPasoUsado = paso;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public String getSecretoCifrado() {
        return secretoCifrado;
    }

    public boolean isActivo() {
        return activo;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public LocalDateTime getActivadoEn() {
        return activadoEn;
    }

    public Long getUltimoPasoUsado() {
        return ultimoPasoUsado;
    }
}
