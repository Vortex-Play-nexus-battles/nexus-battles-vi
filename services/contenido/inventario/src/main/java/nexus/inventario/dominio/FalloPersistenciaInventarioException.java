package nexus.inventario.dominio;

public class FalloPersistenciaInventarioException extends RuntimeException {

    public FalloPersistenciaInventarioException(Throwable causa) {
        super("No fue posible guardar el inventario.", causa);
    }
}
