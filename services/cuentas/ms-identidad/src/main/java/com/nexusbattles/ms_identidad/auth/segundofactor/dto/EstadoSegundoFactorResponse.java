package com.nexusbattles.ms_identidad.auth.segundofactor.dto;

/**
 * {@code EstadoSegundoFactor} de ms-identidad-auth.yaml 2.2.0. Nunca lleva el
 * secreto ni los codigos de recuperacion: solo cuantos quedan.
 *
 * @param activadoEn instante ISO-8601 en UTC, o nulo si no esta activo
 * @param codigosRecuperacionRestantes nulo si no esta activo
 */
public record EstadoSegundoFactorResponse(boolean activo,
                                          boolean obligatorio,
                                          boolean disponible,
                                          boolean enrolamientoPendiente,
                                          String activadoEn,
                                          Long codigosRecuperacionRestantes) {
}
