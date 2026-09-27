package com.nexusbattles.ms_identidad.auth.validation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Respuesta de la verificacion (moderacion-lista-negra.yaml 2.0.0). Solo se
 * leen {@code aprobado}, {@code accion} y {@code motivo}; el resto
 * (categoria, coincidencias) se ignora: identidad no necesita saber que
 * termino coincidio, y nunca lo muestra.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ListaNegraResponse {

    public static final String RECHAZAR = "RECHAZAR";

    private boolean aprobado;
    private String accion;
    private String motivo;
}
