package com.nexusbattles.ms_identidad.auth.recuperacion;

/**
 * Rechazo de la recuperacion o de la configuracion de preguntas (B1), con el
 * {@code type} estable del contrato y su estado HTTP. Mismo patron que
 * {@code CambioDePasswordRechazadoException}.
 */
public class RecuperacionRechazadaException extends RuntimeException {

    public enum Motivo {
        /** Las respuestas de seguridad no coinciden; cuenta como intento fallido del codigo. */
        RESPUESTAS_INCORRECTAS("respuestas-incorrectas", "Respuestas incorrectas", 422),
        /** La contrasena nueva incumple RF-AUT-002; el detalle dice que regla. */
        POLITICA("contrasena-no-cumple-politica", "La contraseña nueva no cumple la política", 422),
        /** Cantidad fuera de rango, pregunta o respuesta vacia o larga, preguntas repetidas. */
        PREGUNTAS_INVALIDAS("preguntas-invalidas", "Preguntas de seguridad inválidas", 422),
        /** La contrasena actual no coincide; cuenta como intento fallido de la cuenta (RF-AUT-009). */
        ACTUAL_INCORRECTA("contrasena-actual-incorrecta", "La contraseña actual es incorrecta", 422),
        /** RF-AUT-009: la cuenta esta bloqueada por intentos fallidos. */
        CUENTA_BLOQUEADA("cuenta-bloqueada", "Cuenta bloqueada temporalmente", 423);

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

    public RecuperacionRechazadaException(Motivo motivo, String detalle) {
        super(detalle);
        this.motivo = motivo;
    }

    public Motivo getMotivo() {
        return motivo;
    }
}
