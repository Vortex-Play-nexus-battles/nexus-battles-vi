package com.nexusbattles.ms_ecommerce.service;

/**
 * La cantidad pedida para una linea del carrito no se puede aplicar, y por que
 * (contrato 1.4.0). El motivo es de dominio; el codigo HTTP y el {@code type}
 * los pone {@code ManejadorDeErrores}.
 */
public class CantidadNoPermitidaException extends RuntimeException {

    /** Por que no se puede. */
    public enum Motivo {
        /** Fuera de 1..20: 400 {@code cantidad-fuera-de-rango}. */
        FUERA_DE_RANGO,
        /** Sumada a lo que ya habia pasa de 20: 409 {@code cantidad-maxima-por-linea}. */
        MAXIMA_POR_LINEA,
        /** Pasa de las unidades que le quedan al producto: 409 {@code tiraje-insuficiente}. */
        TIRAJE_INSUFICIENTE
    }

    private final Motivo motivo;
    private final Integer disponibles;

    public CantidadNoPermitidaException(Motivo motivo, String mensaje, Integer disponibles) {
        super(mensaje);
        this.motivo = motivo;
        this.disponibles = disponibles;
    }

    public static CantidadNoPermitidaException fueraDeRango(int cantidad) {
        return new CantidadNoPermitidaException(Motivo.FUERA_DE_RANGO,
                "La cantidad por linea va de 1 a " + CotizadorDelCarrito.MAXIMO_POR_LINEA + " (llego " + cantidad + ").",
                null);
    }

    public static CantidadNoPermitidaException maximaPorLinea(int yaHabia) {
        return new CantidadNoPermitidaException(Motivo.MAXIMA_POR_LINEA,
                "Una linea admite hasta " + CotizadorDelCarrito.MAXIMO_POR_LINEA + " unidades y ya tienes "
                        + yaHabia + " de este producto.", null);
    }

    public static CantidadNoPermitidaException tirajeInsuficiente(int quedan) {
        return new CantidadNoPermitidaException(Motivo.TIRAJE_INSUFICIENTE,
                quedan == 1 ? "Solo queda 1 unidad de este producto." : "Solo quedan " + quedan
                        + " unidades de este producto.", quedan);
    }

    public Motivo motivo() {
        return motivo;
    }

    /** Las unidades que le quedan al producto, en {@code TIRAJE_INSUFICIENTE}. */
    public Integer disponibles() {
        return disponibles;
    }
}
