package nexus.inventario.aplicacion;

public class CatalogoNoDisponibleException extends RuntimeException {

    public CatalogoNoDisponibleException(Throwable causa) {
        super("No fue posible verificar el producto en el catalogo. Intenta nuevamente.", causa);
    }
}
