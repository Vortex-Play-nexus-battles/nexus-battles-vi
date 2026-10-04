package com.nexusbattles.ms_ecommerce.seguridad;

import org.springframework.web.client.RestClientException;

/**
 * No hay credencial de servicio: no esta configurada, el emisor no responde o
 * rechazo el {@code client_id}/{@code client_secret}.
 *
 * <p>Es una {@link RestClientException} por el mismo motivo que
 * {@code CredencialDeServicioNoDisponible} de plataforma-seguridad: se lanza
 * desde dentro del {@code RestClient} (el interceptor pide el token justo
 * antes de la llamada) y cada cliente ya traduce {@code RestClientException} a
 * «servicio no disponible». Sin credencial no hay llamada, y eso es
 * exactamente eso. Nunca se resuelve inventando un token.
 */
public class CredencialDeServicioNoDisponibleException extends RestClientException {

    public CredencialDeServicioNoDisponibleException(String mensaje) {
        super(mensaje);
    }

    public CredencialDeServicioNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
