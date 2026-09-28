package com.nexusbattles.ms_ecommerce.compra.pago;

import com.nexusbattles.ms_ecommerce.precios.Moneda;

/**
 * Cuerpo de {@code POST /checkout} ({@code SolicitudDePago}, contrato 1.3.0):
 * el formulario de pago del 7.5 —titular, numero, vencimiento y codigo— y la
 * moneda en que se paga.
 *
 * <p><b>Sin anotaciones de validacion, a proposito.</b> Un rechazo de Bean
 * Validation lleva el valor rechazado en su mensaje, y Spring lo escribe en la
 * bitacora al resolverlo: un numero de tarjeta mal escrito acabaria entero en
 * el agregador de logs. La validacion la hace {@link ValidadorDeTarjeta}, que
 * dice que campo fallo y nunca su valor.
 *
 * <p>{@link #toString()} tampoco los ensena. El numero y el codigo viven en
 * memoria lo que dura la peticion: no se guardan en ningun sitio.
 *
 * @param moneda null = COP (el valor por omision del contrato)
 */
public record SolicitudDePago(String titular, String numeroTarjeta, String vencimiento, String codigoSeguridad,
                              Moneda moneda) {

    /** La moneda pedida, o COP si no se dijo. */
    public Moneda monedaPedida() {
        return moneda == null ? Moneda.COP : moneda;
    }

    @Override
    public String toString() {
        return "SolicitudDePago[moneda=" + monedaPedida() + ", datos de tarjeta ocultos]";
    }
}
