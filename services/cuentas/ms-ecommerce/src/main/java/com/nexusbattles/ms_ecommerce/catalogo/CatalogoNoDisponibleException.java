package com.nexusbattles.ms_ecommerce.catalogo;

/**
 * El catalogo maestro no se pudo consultar: no respondio a tiempo, rechazo la
 * conexion, respondio con un 5xx o con algo que no se puede leer.
 *
 * <p>La vitrina y el carrito la traducen en un 503 con {@code Retry-After}
 * (ver {@code ManejadorDeErrores}): sin catalogo no hay precio ni existencias
 * que validar, y fallar a la vista es mejor que vender a ciegas o colgar la
 * peticion hasta que el borde la corte.
 */
public class CatalogoNoDisponibleException extends RuntimeException {

    public CatalogoNoDisponibleException(String mensaje) {
        super(mensaje);
    }

    public CatalogoNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
