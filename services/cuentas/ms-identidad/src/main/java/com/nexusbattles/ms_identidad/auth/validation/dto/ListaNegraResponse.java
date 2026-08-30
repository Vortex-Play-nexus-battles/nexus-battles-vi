package com.nexusbattles.ms_identidad.auth.validation.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ListaNegraResponse {
    private boolean aprobado;
    private String motivo;
}
