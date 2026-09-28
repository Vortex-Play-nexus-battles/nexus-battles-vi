package com.nexusbattles.ms_ecommerce.integracion.correo;

import com.nexusbattles.ms_ecommerce.integracion.ConfiguracionDeIntegraciones;
import com.nexusbattles.ms_ecommerce.integracion.ServicioNoDisponibleException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.List;

/**
 * El correo de confirmacion de compra (7.5: «el cliente debe recibir un correo
 * electronico de confirmacion de compra con el detalle de los productos
 * adquiridos y el total pagado»), por el servicio de correo
 * ({@code POST /api/v1/correos/confirmacion-compra}, correo.yaml 1.4.0).
 *
 * <p>Con la credencial de la tienda (correo solo atiende a servicios) y con
 * {@code Idempotency-Key}: correo 1.4.0 guarda cada envio en su cola
 * persistente, y la misma clave dos veces no encola dos correos. Por eso el
 * reintento de una orden no manda la confirmacion dos veces.
 */
@Component
public class ClienteDeCorreo {

    static final String RUTA = "/api/v1/correos/confirmacion-compra";
    static final String CABECERA_DE_IDEMPOTENCIA = "Idempotency-Key";

    /** Lo que hizo el servicio de correo con la peticion. */
    public enum Resultado {
        /** Guardado en su cola: lo entrega su trabajador, con reintentos. */
        ACEPTADO,
        /** No acepto los datos (400): repetirlos no lo va a cambiar. */
        RECHAZADO
    }

    private final RestClient cliente;

    public ClienteDeCorreo(@Qualifier(ConfiguracionDeIntegraciones.CORREO) RestClient cliente) {
        this.cliente = cliente;
    }

    /**
     * @param clave {@code compra-{id}}, la misma en cada reintento
     * @throws ServicioNoDisponibleException si correo no respondio o no acepto
     *         la credencial de la tienda
     */
    public Resultado enviarConfirmacionDeCompra(ConfirmacionDeCompra confirmacion, String clave) {
        try {
            return cliente.post()
                    .uri(RUTA)
                    .header(CABECERA_DE_IDEMPOTENCIA, clave)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(confirmacion)
                    .exchange((peticion, respuesta) -> {
                        int estado = respuesta.getStatusCode().value();
                        if (respuesta.getStatusCode().is2xxSuccessful()) {
                            return Resultado.ACEPTADO;
                        }
                        if (estado == 400 || estado == 422) {
                            return Resultado.RECHAZADO;
                        }
                        throw new ServicioNoDisponibleException("correo",
                                "El servicio de correo respondio " + estado);
                    });
        } catch (RestClientException | IllegalArgumentException fallo) {
            throw new ServicioNoDisponibleException("correo",
                    "No se pudo pedir el correo de confirmacion: " + fallo.getMessage(), fallo);
        }
    }

    /**
     * {@code CorreoConfirmacionCompraRequest} de correo.yaml 1.4.0.
     *
     * @param fechaHora ISO-8601 con zona: cuando la pasarela aprobo el pago
     */
    public record ConfirmacionDeCompra(String email, String apodo, BigDecimal monto, String moneda,
                                       String concepto, String fechaHora, List<Linea> lineas, String orden) {

        /** El destinatario no se escribe en la bitacora. */
        @Override
        public String toString() {
            return "ConfirmacionDeCompra[orden=" + orden + ", moneda=" + moneda + ", lineas=" + lineas.size() + "]";
        }
    }

    public record Linea(String nombre, int cantidad, BigDecimal precioUnitario, BigDecimal subtotal) {
    }
}
