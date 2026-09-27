package com.nexusbattles.ms_identidad.sanciones;

import java.time.OffsetDateTime;
import java.util.UUID;

/** {@code EstadoDeCuenta} de ms-identidad-admin.yaml: como quedo la cuenta tras la proyeccion. */
public record EstadoDeCuentaResponse(UUID uid, String estado, OffsetDateTime suspendidoHasta, int versionToken) {
}
