package com.nexusbattles.ms_identidad.auth.exception;

import java.net.URI;

/**
 * Rechazo de un cambio de contraseña — HU-AUT-006.
 *
 * <p>Lleva el {@code type} estable con el que la interfaz decide qué mostrar
 * (CA-06: problem details, no texto plano). Un solo tipo de excepción con
 * cuatro motivos, para que el manejador sea uno y las pruebas afirmen el
 * motivo por su URI y no por la redacción.
 */
public class CambioDePasswordRechazadoException extends RuntimeException {

    /** Por qué se rechazó. Cada motivo tiene su {@code type} y su estado HTTP. */
    public enum Motivo {
        /** CA-02: la actual no coincide. Cuenta como intento fallido (RF-AUT-009). */
        ACTUAL_INCORRECTA("contrasena-actual-incorrecta", "La contraseña actual es incorrecta", 422),
        /** CA-03: la nueva incumple la política; el detalle dice qué regla. */
        POLITICA("contrasena-no-cumple-politica", "La contraseña nueva no cumple la política", 422),
        /** CA-03: la nueva es igual a la anterior. */
        REPETIDA("contrasena-repetida", "La contraseña nueva es igual a la anterior", 422),
        /** La confirmación no coincide con la nueva. */
        CONFIRMACION("contrasena-confirmacion-no-coincide", "La confirmación no coincide", 422),
        /** RF-AUT-009: la cuenta está bloqueada por intentos fallidos. */
        CUENTA_BLOQUEADA("cuenta-bloqueada", "Cuenta bloqueada temporalmente", 423);

        private final String slug;
        private final String titulo;
        private final int estado;

        Motivo(String slug, String titulo, int estado) {
            this.slug = slug;
            this.titulo = titulo;
            this.estado = estado;
        }

        public URI tipo() {
            return URI.create("https://nexusbattles.upb.edu.co/errors/" + slug);
        }

        public String titulo() {
            return titulo;
        }

        public int estado() {
            return estado;
        }
    }

    private final Motivo motivo;

    public CambioDePasswordRechazadoException(Motivo motivo, String detalle) {
        super(detalle);
        this.motivo = motivo;
    }

    public Motivo getMotivo() {
        return motivo;
    }
}
