package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.springframework.web.client.HttpClientErrorException;

import java.util.Set;

/**
 * Que 4xx es un rechazo y cual un fallo pasajero, igual para los destinos de
 * identidad y de correo.
 *
 * <p>401 y 403 no son un «no» del destino a la salida: dicen que la
 * credencial de servicio de este modulo no vale todavia (secreto sin
 * registrar en ms-identidad, emisor sin la clave). 408, 425 y 429 piden
 * volver mas tarde. Los cinco se reintentan. El resto de 4xx (400, 404, 422)
 * es la respuesta del destino a esta salida en concreto: un reintento a ciegas
 * no lo arregla.
 */
final class Respuestas {

    private static final Set<Integer> PASAJEROS = Set.of(401, 403, 408, 425, 429);

    private Respuestas() {
    }

    /** Relanza si el 4xx es pasajero; si no, lo da por rechazo. */
    static DestinoDeSalidas.Resultado rechazoOReintento(HttpClientErrorException error) {
        if (PASAJEROS.contains(error.getStatusCode().value())) {
            throw error;
        }
        return DestinoDeSalidas.Resultado.RECHAZADO;
    }
}
