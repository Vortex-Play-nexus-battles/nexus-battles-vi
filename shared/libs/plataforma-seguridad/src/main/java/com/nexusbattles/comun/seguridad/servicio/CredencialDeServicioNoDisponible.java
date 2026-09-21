package com.nexusbattles.comun.seguridad.servicio;

import org.springframework.web.client.RestClientException;

/**
 * El emisor no entrego una credencial de servicio: no responde, o rechazo el
 * {@code client_id}/{@code client_secret}.
 *
 * <p>Es de disponibilidad, no de negocio: quien llama decide si reintenta o si
 * degrada la seccion (HU-DIS-003). Nunca se resuelve inventando un token.
 *
 * <p><b>Es una {@link RestClientException}</b> y no una {@code RuntimeException}
 * suelta a proposito: se lanza desde dentro del {@code RestClient} (el
 * interceptor pide el token justo antes de la llamada), y cada adaptador ya
 * traduce {@code RestClientException} a su «no disponible» (503). Como
 * excepcion suelta se colaba por encima de esos {@code catch} y salia como
 * 500 sin explicacion — paso en el host de dev cuando la URL del emisor era la
 * de un Keycloak que no existe. Sin credencial no hay llamada, y eso es
 * exactamente «el servicio de al lado no esta disponible».
 */
public class CredencialDeServicioNoDisponible extends RestClientException {

    public CredencialDeServicioNoDisponible(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
