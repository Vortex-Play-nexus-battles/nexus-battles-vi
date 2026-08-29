package com.nexusbattles.ms_identidad.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LoginResponse {
    private Long usuarioId;
    private String apodo;
    private String email;
    private String rol;
    private boolean dispositivoNuevo;
}
