package com.nexusbattles.ms_identidad.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Cuerpo de {@code PUT /api/v1/auth/password} — HU-AUT-006 (RF-AUT-006).
 *
 * <p>No lleva identificador de usuario: quien cambia la contraseña es quien
 * firma el token. Aceptarlo en el cuerpo permitiría cambiarle la clave a otro.
 */
@Getter
@Setter
public class CambiarPasswordRequest {

    @NotBlank
    private String passwordActual;

    @NotBlank
    private String nuevaPassword;

    @NotBlank
    private String confirmacion;
}
