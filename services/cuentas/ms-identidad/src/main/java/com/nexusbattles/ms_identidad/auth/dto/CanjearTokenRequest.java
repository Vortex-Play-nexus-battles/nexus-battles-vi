package com.nexusbattles.ms_identidad.auth.dto;

import com.nexusbattles.ms_identidad.auth.recuperacion.RespuestaDeSeguridad;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * {@code CanjearTokenRequest} de ms-identidad-auth.yaml 2.x.
 *
 * <p><b>2.0.0, incompatible:</b> antes llevaba solo {@code token}. Desde B1 el
 * codigo no esta en la base (solo su resumen BCrypt), asi que sin el correo
 * no hay forma de encontrar su fila sin volver a guardar el secreto. Lleva
 * {@code email} + {@code codigo} y, si la cuenta configuro preguntas de
 * seguridad, {@code respuestas}.
 *
 * <p>Sin {@code toString} de Lombok: lleva el codigo y la contrasena nueva.
 */
@Getter
@Setter
public class CanjearTokenRequest {

    @NotBlank
    @Size(max = 254)
    private String email;

    @NotBlank
    @Size(max = 64)
    private String codigo;

    private List<RespuestaDeSeguridad> respuestas;

    @NotBlank
    @Size(min = 9, message = "La contraseña debe tener más de 8 caracteres")
    private String nuevaPassword;

    @Override
    public String toString() {
        return "CanjearTokenRequest[codigo=********, nuevaPassword=********]";
    }
}
