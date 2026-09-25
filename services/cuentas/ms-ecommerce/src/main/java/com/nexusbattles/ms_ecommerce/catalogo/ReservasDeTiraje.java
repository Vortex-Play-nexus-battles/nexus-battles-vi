package com.nexusbattles.ms_ecommerce.catalogo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexusbattles.ms_ecommerce.integracion.ConfiguracionDeIntegraciones;
import com.nexusbattles.ms_ecommerce.integracion.ServicioNoDisponibleException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Reserva de tiraje en el catalogo al vender
 * ({@code POST /api/v1/productos/{id}/adquisiciones}, productos.yaml 1.4.0,
 * HU-PRD-002). Una llamada reserva UNA unidad; una orden de tres unidades hace
 * tres llamadas, cada una con su clave.
 *
 * <p>Solo la puede usar un servicio (rol SERVICIO), por eso va con la
 * credencial de la tienda. Es idempotente por {@code Idempotency-Key}: el
 * catalogo guarda la clave con su resultado, asi que repetirla —porque la
 * respuesta anterior no llego, o porque la orden se retoma tras un reinicio—
 * devuelve lo mismo sin descontar otra unidad.
 *
 * <p>Lo que el catalogo decide (aceptada, agotado, suspendido, inexistente)
 * vuelve como {@link Resultado}; lo que es una averia, como
 * {@link ServicioNoDisponibleException}. Mezclarlos seria reembolsar una
 * compra porque el catalogo tardo, o reintentar para siempre una que se agoto.
 */
@Component
public class ReservasDeTiraje {

    static final String RUTA = "/api/v1/productos/{id}/adquisiciones";
    static final String CABECERA_DE_IDEMPOTENCIA = "Idempotency-Key";

    /** Lo que el catalogo respondio a la reserva de una unidad. */
    public enum Resultado {
        /** Unidad reservada (o ya reservada con esa misma clave). */
        ACEPTADA,
        /** No quedan unidades. */
        AGOTADO,
        /** El producto esta suspendido: no se vende. */
        SUSPENDIDO,
        /** El catalogo no tiene ese producto. */
        INEXISTENTE,
        /**
         * El catalogo rechazo la peticion misma: la clave ya se habia usado
         * con otro producto (409 problem) o no le parecio valida (400). No se
         * arregla repitiendola.
         */
        RECHAZADA;

        public boolean aceptada() {
            return this == ACEPTADA;
        }
    }

    private final RestClient cliente;

    public ReservasDeTiraje(@Qualifier(ConfiguracionDeIntegraciones.RESERVAS) RestClient cliente) {
        this.cliente = cliente;
    }

    /**
     * @param clave la de esta unidad de esta linea de esta orden; la misma en
     *              cada reintento
     * @throws ServicioNoDisponibleException si el catalogo no respondio, o no
     *         acepto la credencial de la tienda
     */
    public Resultado reservarUnaUnidad(String productoId, String clave) {
        try {
            return cliente.post()
                    .uri(RUTA, productoId)
                    .header(CABECERA_DE_IDEMPOTENCIA, clave)
                    .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON)
                    .exchange((peticion, respuesta) -> {
                        int estado = respuesta.getStatusCode().value();
                        if (estado == 404) {
                            return Resultado.INEXISTENTE;
                        }
                        if (estado == 400) {
                            return Resultado.RECHAZADA;
                        }
                        if (estado == 200 || estado == 409) {
                            MediaType tipo = respuesta.getHeaders().getContentType();
                            if (tipo != null && MediaType.APPLICATION_PROBLEM_JSON.isCompatibleWith(tipo)) {
                                // 409 como problem details: la clave ya se uso con otro producto.
                                return Resultado.RECHAZADA;
                            }
                            ResultadoAdquisicion cuerpo = respuesta.bodyTo(ResultadoAdquisicion.class);
                            return interpretar(estado, cuerpo);
                        }
                        throw new ServicioNoDisponibleException("productos",
                                "El catalogo respondio " + estado + " a la reserva de " + productoId);
                    });
        } catch (RestClientException | IllegalArgumentException fallo) {
            throw new ServicioNoDisponibleException("productos",
                    "No se pudo reservar tiraje de " + productoId + ": " + fallo.getMessage(), fallo);
        }
    }

    private static Resultado interpretar(int estado, ResultadoAdquisicion cuerpo) {
        String resultado = cuerpo == null || cuerpo.estado() == null ? "" : cuerpo.estado();
        return switch (resultado) {
            case "ACEPTADA" -> estado == 200 ? Resultado.ACEPTADA : Resultado.RECHAZADA;
            case "AGOTADO" -> Resultado.AGOTADO;
            case "SUSPENDIDO" -> Resultado.SUSPENDIDO;
            // Un 200 sin cuerpo legible sigue siendo la reserva hecha; un 409
            // sin decir por que no se puede tomar por reservada.
            default -> estado == 200 ? Resultado.ACEPTADA : Resultado.RECHAZADA;
        };
    }

    /** {@code ResultadoAdquisicion} del contrato de productos. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ResultadoAdquisicion(String estado, String mensaje) {
    }
}
