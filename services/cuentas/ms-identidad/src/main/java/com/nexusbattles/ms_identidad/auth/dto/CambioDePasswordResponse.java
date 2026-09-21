package com.nexusbattles.ms_identidad.auth.dto;

/**
 * Respuesta de un cambio de contraseña correcto — HU-AUT-006, CA-04.
 *
 * <p>Trae un <b>token nuevo</b> para esta sesión: el cambio sube la versión
 * de token del usuario (misma mecánica que HU-RBAC-003) y con eso caducan
 * todas las sesiones abiertas, incluida la que hizo el cambio. Para que esa
 * siga válida se le emite uno nuevo con la versión vigente; el cliente lo
 * guarda en lugar del anterior.
 *
 * @param token   JWT con la versión nueva, para esta sesión
 * @param mensaje confirmación legible
 */
public record CambioDePasswordResponse(String token, String mensaje) {
}
