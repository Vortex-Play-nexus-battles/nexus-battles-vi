package com.nexusbattles.ms_identidad.auth.correo.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CorreoConfirmacionCuentaRequest {
    private String email;
    private String apodo;
    private String codigo;
    private int minutosVigencia;
}
