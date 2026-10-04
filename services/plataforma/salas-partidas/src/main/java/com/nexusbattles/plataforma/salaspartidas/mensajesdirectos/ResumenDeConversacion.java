package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos;

import java.util.UUID;

/**
 * Una fila de «mis conversaciones» — esquema {@code ResumenDeConversacion} de
 * {@code contracts/openapi/salas-partidas.yaml} 1.8.0.
 *
 * @param uidOtro       con quien es la conversacion
 * @param apodoOtro     su apodo tal como quedo en el ultimo mensaje, o {@code null}
 * @param ultimoMensaje el mas reciente de los dos
 * @param noLeidos      los que el otro escribio y quien pregunta no ha leido
 * @param estado        si quien pregunta puede escribir en ella (D-40)
 */
public record ResumenDeConversacion(UUID uidOtro, String apodoOtro, MensajeDirecto ultimoMensaje, long noLeidos,
                                    EstadoDeConversacion estado) {

    public ResumenDeConversacion {
        estado = estado == null ? EstadoDeConversacion.ACTIVA : estado;
    }

    /** Sin bloqueos de por medio. */
    public ResumenDeConversacion(UUID uidOtro, String apodoOtro, MensajeDirecto ultimoMensaje, long noLeidos) {
        this(uidOtro, apodoOtro, ultimoMensaje, noLeidos, EstadoDeConversacion.ACTIVA);
    }
}
