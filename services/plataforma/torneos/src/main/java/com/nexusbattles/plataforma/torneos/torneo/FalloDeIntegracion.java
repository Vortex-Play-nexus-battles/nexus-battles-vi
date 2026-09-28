package com.nexusbattles.plataforma.torneos.torneo;

/**
 * Una llamada a otro servicio que no salio bien, ya clasificada por el
 * adaptador que la hizo: {@link #reintentable()} dice si vale la pena volver a
 * intentarla (el proveedor no responde, tarda de mas, esta saturado o aun no
 * tiene la ruta desplegada) o si el proveedor la rechazo por algo que otro
 * intento no va a cambiar (la reserva ya estaba liberada, el producto no
 * existe). El procesador de operaciones decide con esto entre reintentar y
 * dejarla para revision.
 */
public class FalloDeIntegracion extends RuntimeException {

    private final boolean reintentable;

    private FalloDeIntegracion(String detalle, boolean reintentable, Throwable causa) {
        super(detalle, causa);
        this.reintentable = reintentable;
    }

    public static FalloDeIntegracion pasajero(String detalle) {
        return new FalloDeIntegracion(detalle, true, null);
    }

    public static FalloDeIntegracion pasajero(String detalle, Throwable causa) {
        return new FalloDeIntegracion(detalle, true, causa);
    }

    public static FalloDeIntegracion definitivo(String detalle) {
        return new FalloDeIntegracion(detalle, false, null);
    }

    public boolean reintentable() {
        return reintentable;
    }
}
