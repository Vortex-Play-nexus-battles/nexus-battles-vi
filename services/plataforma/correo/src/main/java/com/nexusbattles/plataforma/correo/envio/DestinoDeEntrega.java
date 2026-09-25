package com.nexusbattles.plataforma.correo.envio;

/**
 * A que servidor se entrego un correo.
 *
 * <p>La distincion es la que permite decir si un correo llego a alguien: lo
 * que acepto el buzon de pruebas (Mailpit) no iba a ninguna bandeja real, y
 * contarlo junto a lo del proveedor diria que salieron correos que nadie iba
 * a recibir -- justo el engano que R18 vino a quitar.
 */
public enum DestinoDeEntrega {
    /** El servidor principal (el proveedor, fuera de desarrollo local). */
    PROVEEDOR,
    /** El buzon al que se desvian las direcciones reservadas (RFC 2606). */
    BUZON_DE_PRUEBAS
}
