package com.nexusbattles.ms_identidad.auth.correo.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CorreoAvisoAccesoRequest {
    private String email;
    private String apodo;
    private String ip;
    private String fechaHora;
}
