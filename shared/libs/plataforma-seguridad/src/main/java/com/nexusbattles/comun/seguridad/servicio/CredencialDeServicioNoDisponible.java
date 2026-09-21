package com.nexusbattles.comun.seguridad.servicio;

/**
 * El emisor no entrego una credencial de servicio: no responde, o rechazo el
 * {@code client_id}/{@code client_secret}.
 *
 * <p>Es de disponibilidad, no de negocio: quien llama decide si reintenta o si
 * degrada la seccion (HU-DIS-003). Nunca se resuelve inventando un token.
 */
public class CredencialDeServicioNoDisponible extends RuntimeException {

    public CredencialDeServicioNoDisponible(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
