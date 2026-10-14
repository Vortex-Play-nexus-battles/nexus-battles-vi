package com.nexusbattles.ms_identidad.admin.inventario;

/**
 * El inventario decidio y dijo que no (4xx): el producto no existe (422), esta
 * suspendido o la clave ya se uso con otra entrega (409). Se responde con el
 * mismo estado y su detalle: es lo que la persona tiene que leer para
 * corregir. No se aplico nada.
 */
public class EntregaRechazadaException extends RuntimeException {

    private final int estado;

    public EntregaRechazadaException(int estado, String detalle) {
        super(detalle);
        this.estado = estado;
    }

    public int getEstado() {
        return estado;
    }
}
