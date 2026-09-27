package com.nexusbattles.ms_subastas.notificaciones;

/** Salida por correo de un aviso del outbox (B8). */
public enum EstadoCorreo {

    /** Por enviar, o esperando el siguiente reintento. */
    PENDIENTE,

    /** Correo lo acepto (202: guardado en su cola persistente). */
    ENVIADO,

    /** No hay a quien escribirle: la cuenta no existe, no tiene correo o esta baneada. */
    DESCARTADO,

    /** Rechazado por algo que reintentar no arregla, o agotados los reintentos. */
    FALLIDO
}
