package com.nexusbattles.ms_ecommerce.compra.pago;

/**
 * El formulario de pago no pasa la validacion: 400
 * {@code datos-de-pago-invalidos} con el {@code campo}. El mensaje dice que
 * esta mal, nunca el valor que llego.
 */
public class DatosDePagoInvalidosException extends RuntimeException {

    private final String campo;

    public DatosDePagoInvalidosException(String campo, String mensaje) {
        super(mensaje);
        this.campo = campo;
    }

    /** titular, numeroTarjeta, vencimiento o codigoSeguridad. */
    public String campo() {
        return campo;
    }
}
