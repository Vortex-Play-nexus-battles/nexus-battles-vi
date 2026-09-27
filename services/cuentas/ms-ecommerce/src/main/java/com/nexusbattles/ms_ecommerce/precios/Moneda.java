package com.nexusbattles.ms_ecommerce.precios;

/**
 * Las monedas en que la tienda ensena y cobra (7.5 del documento: «precio COP
 * o dolar o euro dependiendo de su ubicacion geografica»).
 *
 * <p>COP es la moneda del catalogo ({@code precioMonedaReal}) y siempre esta
 * disponible. USD y EUR se calculan desde COP con la tasa que publique
 * admin-parametros; sin tasa, no se ofrecen (ver {@link TasasDeCambio}).
 *
 * <p>La escala es parte de la regla de redondeo: el peso colombiano no usa
 * centavos en la practica, el dolar y el euro si.
 */
public enum Moneda {

    COP(0),
    USD(2),
    EUR(2);

    private final int escala;

    Moneda(int escala) {
        this.escala = escala;
    }

    /** Decimales con los que se expresa un precio en esta moneda. */
    public int escala() {
        return escala;
    }

    /** La moneda del catalogo: la base desde la que se convierte. */
    public boolean esLaDelCatalogo() {
        return this == COP;
    }
}
