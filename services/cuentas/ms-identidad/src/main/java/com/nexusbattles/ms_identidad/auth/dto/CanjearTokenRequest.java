package com.nexusbattles.ms_identidad.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CanjearTokenRequest {

    @NotBlank
    private String token;

    @NotBlank
    @Size(min = 9, message = "La contraseña debe tener más de 8 caracteres")
    private String nuevaPassword;
}
