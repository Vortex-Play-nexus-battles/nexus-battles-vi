package com.nexusbattles.ms_identidad.auth.correo.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CorreoBienvenidaRequest {
    private String email;
    private String apodo;
    private String nombres;
    private String apellidos;
}
