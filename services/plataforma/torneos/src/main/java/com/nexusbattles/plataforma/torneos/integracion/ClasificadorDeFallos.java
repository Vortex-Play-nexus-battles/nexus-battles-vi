package com.nexusbattles.plataforma.torneos.integracion;

import com.nexusbattles.plataforma.torneos.torneo.FalloDeIntegracion;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

/**
 * Traduce un fallo HTTP a {@link FalloDeIntegracion} diciendo si vale la pena
 * reintentar.
 *
 * <p>Pasajeros: el proveedor no responde o tarda de mas (timeout, conexion
 * rechazada), esta caido o saturado (5xx, 408, 425, 429), o todavia no
 * reconoce la credencial de servicio (401/403: se arregla desplegando la
 * credencial, no cambiando la peticion). Definitivos: el resto de 4xx, que
 * significan que el proveedor entendio la peticion y la rechazo por su
 * contenido; otro intento identico recibiria lo mismo. Un 404 depende de la
 * ruta: para una reserva o una cuenta es «no existe» (definitivo); para una
 * ruta que el proveedor aun no despliega (una operacion pendiente de su
 * contrato) es pasajero.
 */
final class ClasificadorDeFallos {

    private ClasificadorDeFallos() {
    }

    static FalloDeIntegracion clasificar(String que, RestClientException fallo, boolean noEncontradoEsDefinitivo) {
        if (fallo instanceof HttpStatusCodeException http) {
            int estado = http.getStatusCode().value();
            String detalle = que + ": el proveedor respondio " + estado;
            if (estado == 404) {
                return noEncontradoEsDefinitivo ? FalloDeIntegracion.definitivo(detalle) : FalloDeIntegracion.pasajero(detalle);
            }
            if (estado >= 500 || estado == 401 || estado == 403 || estado == 408 || estado == 425 || estado == 429) {
                return FalloDeIntegracion.pasajero(detalle);
            }
            return FalloDeIntegracion.definitivo(detalle);
        }
        if (fallo instanceof ResourceAccessException) {
            return FalloDeIntegracion.pasajero(que + ": el proveedor no responde (" + fallo.getClass().getSimpleName() + ")", fallo);
        }
        return FalloDeIntegracion.pasajero(que + ": " + fallo.getClass().getSimpleName(), fallo);
    }
}
