package com.nexusbattles.ms_identidad.auth.validation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * {@code POST /lista-negra/verificar} (moderacion-lista-negra.yaml 2.0.0).
 * Desde B2 lleva {@code contexto}: la politica de moderacion decide la accion
 * segun donde va el texto, y para un apodo es RECHAZAR.
 */
@Getter
@AllArgsConstructor
public class ListaNegraRequest {

    public static final String CONTEXTO_APODO = "APODO";

    private String texto;
    private String contexto;
}
