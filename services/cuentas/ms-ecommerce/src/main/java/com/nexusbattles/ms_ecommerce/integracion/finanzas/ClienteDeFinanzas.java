package com.nexusbattles.ms_ecommerce.integracion.finanzas;

import com.nexusbattles.ms_ecommerce.integracion.ConfiguracionDeIntegraciones;
import com.nexusbattles.ms_ecommerce.integracion.ServicioNoDisponibleException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;

/**
 * El asiento del cobro en el libro de moneda real de ms-finanzas
 * ({@code POST /transacciones}, transacciones.yaml 1.0.0, HU-PAG-002).
 *
 * <p>El contrato dice quien tiene que llamarlo: «el servicio que integro la
 * pasarela de pagos simulada, despues de cada intento de cobro». Es la
 * tienda. Solo con credencial de servicio: un jugador no se autodeclara una
 * compra pagada.
 *
 * <p>Idempotente por {@code refId} (el id de la orden): repetirlo responde 409
 * {@code transaccion-ya-registrada}, que para la tienda es exactamente lo
 * mismo que un 201 — el asiento esta.
 */
@Component
public class ClienteDeFinanzas {

    static final String RUTA = "/transacciones";

    /** Lo que se registra: aprobado o rechazado por la pasarela. */
    public enum Resultado {
        APROBADO,
        RECHAZADO
    }

    private final RestClient cliente;

    public ClienteDeFinanzas(@Qualifier(ConfiguracionDeIntegraciones.FINANZAS) RestClient cliente) {
        this.cliente = cliente;
    }

    /**
     * Registra el asiento, o confirma que ya estaba.
     *
     * @throws ServicioNoDisponibleException si ms-finanzas no lo registro por
     *         cualquier motivo: no respondio, no acepto la credencial de la
     *         tienda, o rechazo el cuerpo (un 400 aqui es un defecto de la
     *         tienda que se corrige desplegando, no una decision sobre la
     *         compra: la orden espera y reintenta)
     */
    public void registrar(String refId, String uidUsuario, BigDecimal monto, String moneda, String concepto,
                          Resultado resultado, String referenciaDeLaPasarela) {
        RegistrarTransaccion cuerpo = new RegistrarTransaccion(refId, uidUsuario, monto, moneda, concepto,
                resultado.name(), null, referenciaDeLaPasarela);
        try {
            cliente.post()
                    .uri(RUTA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cuerpo)
                    .exchange((peticion, respuesta) -> {
                        int estado = respuesta.getStatusCode().value();
                        if (estado == 201 || estado == 200 || estado == 409) {
                            return null;
                        }
                        throw new ServicioNoDisponibleException("ms-finanzas",
                                "ms-finanzas respondio " + estado + " al asiento " + refId);
                    });
        } catch (RestClientException | IllegalArgumentException fallo) {
            throw new ServicioNoDisponibleException("ms-finanzas",
                    "No se pudo registrar el asiento " + refId + ": " + fallo.getMessage(), fallo);
        }
    }

    /** {@code RegistrarTransaccion} del contrato de ms-finanzas. */
    record RegistrarTransaccion(String refId, String uidUsuario, BigDecimal monto, String moneda,
                                String concepto, String resultado, String comprobanteUrl,
                                String pasarelaRefExterna) {
    }
}
