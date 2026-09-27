package com.nexusbattles.plataforma.correo.envio;

/**
 * Que paso con UN intento de entrega.
 *
 * <p>{@link EnviadorCorreoService} no lanza: devuelve uno de estos. Asi quien
 * decide el siguiente estado del envio -la cola, con su politica de
 * reintentos- no tiene que adivinar a partir de una excepcion si el fallo se
 * arregla solo esperando o no se va a arreglar nunca.
 */
public sealed interface ResultadoDeEntrega {

    /** El servidor acepto el mensaje. */
    record Entregado(DestinoDeEntrega destino, String identificador) implements ResultadoDeEntrega {
        public Entregado {
            identificador = identificador == null ? "" : identificador;
        }
    }

    /** No se envio a proposito (p. ej. direccion reservada y sin buzon de pruebas). */
    record Omitido(String motivo) implements ResultadoDeEntrega {}

    /**
     * No se pudo enviar.
     *
     * @param permanente true si reintentar no lo arreglaria: destinatario
     *                   rechazado por el servidor (5xx), direccion invalida,
     *                   correo imposible de componer
     * @param motivo     resumen corto y saneado, sin el cuerpo del correo
     */
    record Fallido(boolean permanente, String motivo) implements ResultadoDeEntrega {}

    static ResultadoDeEntrega entregado(DestinoDeEntrega destino, String identificador) {
        return new Entregado(destino, identificador);
    }

    static ResultadoDeEntrega omitido(String motivo) {
        return new Omitido(motivo);
    }

    static ResultadoDeEntrega fallido(boolean permanente, String motivo) {
        return new Fallido(permanente, motivo);
    }

    /** Clasifica una excepcion con {@link ClasificadorDeFallos}. */
    static ResultadoDeEntrega fallido(Throwable error) {
        return new Fallido(ClasificadorDeFallos.esPermanente(error), ClasificadorDeFallos.resumen(error));
    }
}
