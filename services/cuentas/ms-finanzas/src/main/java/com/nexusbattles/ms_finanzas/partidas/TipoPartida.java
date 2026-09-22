package com.nexusbattles.ms_finanzas.partidas;

/**
 * Tipo de partida a efectos de la acreditación de créditos (HU-JUE-012).
 * <ul>
 *   <li>{@link #UNO_A_UNO} — el ganador recibe 2 créditos.</li>
 *   <li>{@link #GRUPAL} — el ganador recibe 4 créditos.</li>
 * </ul>
 * En los dos casos, cada participante no ganador y no sancionado recibe 1
 * crédito por participar.
 */
public enum TipoPartida {
    UNO_A_UNO,
    GRUPAL
}
