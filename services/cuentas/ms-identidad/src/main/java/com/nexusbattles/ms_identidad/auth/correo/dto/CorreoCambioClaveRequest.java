package com.nexusbattles.ms_identidad.auth.correo.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Aviso de que la contraseña cambió — HU-AUT-006 CA-01, sobre la plantilla
 * corporativa (RF-COR-001). Espejo de {@code CorreoCambioClaveRequest} en
 * {@code contracts/openapi/correo.yaml}.
 *
 * <p>No lleva la contraseña, ni la vieja ni la nueva: el correo avisa de que
 * cambió, para que quien no lo hizo reaccione.
 */
@Getter
@AllArgsConstructor
public class CorreoCambioClaveRequest {
    private String email;
    private String apodo;
    private String ip;
    /** ISO-8601 con zona; el servicio de correo lo vuelve legible. */
    private String fechaHora;
}
