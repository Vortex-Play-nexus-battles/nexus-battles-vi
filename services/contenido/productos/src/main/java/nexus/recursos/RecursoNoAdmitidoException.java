package nexus.recursos;

import java.util.Objects;

/**
 * Un archivo que no se puede guardar como recurso gráfico. Lleva el motivo
 * (para el código de estado y el campo {@code motivo} del problem details) y
 * un mensaje que dice el requisito incumplido, que la consola enseña tal cual
 * (HU-PRD-009, criterio 3).
 */
public class RecursoNoAdmitidoException extends RuntimeException {

    /** Por qué se rechaza; cada motivo tiene su código de estado. */
    public enum Motivo {
        /** No llegó archivo, o llegó vacío. 400. */
        ARCHIVO_AUSENTE(400),
        /** La clase no es una de las tres o falta. 400. */
        CLASE_INVALIDA(400),
        /** Pesa más de lo admitido para su clase. 413. */
        DEMASIADO_GRANDE(413),
        /** Mide más de lo admitido para su clase. 413. */
        DIMENSIONES_EXCEDIDAS(413),
        /** No es un formato admitido para su clase. 415. */
        FORMATO_NO_ADMITIDO(415),
        /** Dice ser un formato admitido pero está cortado, corrupto o lleva algo pegado. 415. */
        ARCHIVO_DANADO(415),
        /** Se sube como animación y es una imagen fija. 415. */
        SIN_ANIMACION(415);

        private final int estado;

        Motivo(int estado) {
            this.estado = estado;
        }

        public int estado() {
            return estado;
        }
    }

    private final Motivo motivo;

    public RecursoNoAdmitidoException(Motivo motivo, String mensaje) {
        super(mensaje);
        this.motivo = Objects.requireNonNull(motivo);
    }

    public Motivo motivo() {
        return motivo;
    }
}
