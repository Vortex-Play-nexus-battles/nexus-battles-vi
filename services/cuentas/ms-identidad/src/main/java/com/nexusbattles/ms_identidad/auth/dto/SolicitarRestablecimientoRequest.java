package com.nexusbattles.ms_identidad.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class SolicitarRestablecimientoRequest {

    @Email
    @NotBlank
    private String email;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
