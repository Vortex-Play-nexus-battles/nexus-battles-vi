package com.nexusbattles.ms_identidad.auth.correo.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Confirmacion de que la cuenta se elimino — §7.3.6 («notificacion de
 * confirmacion al completar la eliminacion»), SA-14. Espejo de
 * {@code CorreoCierreCuentaRequest} en {@code contracts/openapi/correo.yaml}
 * 1.7.0.
 *
 * <p>Lleva la direccion y el apodo que la cuenta tenia antes de anonimizarse:
 * es lo ultimo que se le envia a esa direccion, y ninguno de los dos queda
 * guardado en ms-identidad.
 */
@Getter
@AllArgsConstructor
public class CorreoCierreCuentaRequest {
    private String email;
    private String apodo;
    /** ISO-8601 con zona; el servicio de correo lo vuelve legible. */
    private String fechaHora;
}
