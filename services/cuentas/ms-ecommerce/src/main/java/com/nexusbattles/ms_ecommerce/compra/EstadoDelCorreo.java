package com.nexusbattles.ms_ecommerce.compra;

/** El correo de confirmacion de una orden ({@code Orden.correoConfirmacion}, contrato 1.4.0). */
public enum EstadoDelCorreo {
    /** Aun no lo acepto el servicio de correo. */
    PENDIENTE,
    /** El servicio de correo lo guardo en su cola: lo entrega su trabajador. */
    ENVIADO,
    /**
     * No se envia: la orden no llego a entregarse (rechazada, reembolsada), la
     * cuenta ya no existe en ms-identidad, o el entorno apago el envio.
     */
    OMITIDO,
    /** El servicio de correo no acepto los datos de la cuenta. */
    RECHAZADO
}
