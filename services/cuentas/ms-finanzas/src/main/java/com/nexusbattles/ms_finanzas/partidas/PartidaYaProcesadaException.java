package com.nexusbattles.ms_finanzas.partidas;

/**
 * Se lanza cuando {@link AcreditacionPartidaService#procesarResultadoPartida}
 * recibe un {@code partidaId} que ya fue procesado. Es la señal de
 * idempotencia para ms-salas-partidas: un reintento del mismo resultado no
 * duplica créditos ni cofres.
 *
 * <p>Se mapea a 409 Conflict con {@code type} URI estable en
 * {@code GlobalExceptionHandler}, análogo a
 * {@code TransaccionYaRegistradaException} de HU-PAG-002.
 */
public class PartidaYaProcesadaException extends RuntimeException {

    private final String partidaId;

    public PartidaYaProcesadaException(String partidaId) {
        super("La partida " + partidaId + " ya fue procesada.");
        this.partidaId = partidaId;
    }

    public String getPartidaId() {
        return partidaId;
    }
}
