package com.nexusbattles.ms_identidad.privacidad;

/**
 * Por que no se programo el cierre de una cuenta (ms-identidad-perfiles.yaml
 * 1.4.0). Cada motivo tiene su {@code type} estable y su estado HTTP; la
 * interfaz decide por el {@code type}, nunca por la redaccion. En todos los
 * casos no cambio nada.
 */
public class CierreRechazadoException extends RuntimeException {

    public enum Motivo {
        /** RF-AUT-009: la cuenta esta bloqueada por intentos fallidos; no se comparo la contrasena. */
        CUENTA_BLOQUEADA("cuenta-bloqueada", "Cuenta bloqueada temporalmente", 423),
        /** La contrasena no coincide; cuenta como intento fallido de la cuenta (RF-AUT-009). */
        ACTUAL_INCORRECTA("contrasena-actual-incorrecta", "La contraseña actual es incorrecta", 422),
        /** RF-PRV-005, excepciones: subastas activas como vendedor o pujas vigentes. */
        OPERACIONES_PENDIENTES("cierre-con-operaciones-pendientes", "Tienes subastas o pujas abiertas", 409),
        /** ms-subastas no respondio: sin poder comprobarlo, no se programa (fail-closed). */
        SUBASTAS_NO_DISPONIBLES("subastas-no-disponibles", "No pudimos comprobar tus subastas", 503);

        private final String tipo;
        private final String titulo;
        private final int estado;

        Motivo(String tipo, String titulo, int estado) {
            this.tipo = tipo;
            this.titulo = titulo;
            this.estado = estado;
        }

        public String tipo() {
            return tipo;
        }

        public String titulo() {
            return titulo;
        }

        public int estado() {
            return estado;
        }
    }

    private final Motivo motivo;
    private final int subastasActivas;
    private final int pujasVigentes;

    public CierreRechazadoException(Motivo motivo, String detalle) {
        this(motivo, detalle, 0, 0);
    }

    public CierreRechazadoException(Motivo motivo, String detalle, int subastasActivas, int pujasVigentes) {
        super(detalle);
        this.motivo = motivo;
        this.subastasActivas = subastasActivas;
        this.pujasVigentes = pujasVigentes;
    }

    public Motivo getMotivo() {
        return motivo;
    }

    public int getSubastasActivas() {
        return subastasActivas;
    }

    public int getPujasVigentes() {
        return pujasVigentes;
    }
}
