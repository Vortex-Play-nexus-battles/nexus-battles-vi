package nexus.inventario.aplicacion;

/**
 * La parte de armadura que manda quien crea el elemento no es la del producto
 * en el catalogo — B4. La parte la decide el catalogo; es 400, como el tipo que
 * no coincide.
 */
public class ParteNoCoincideException extends RuntimeException {

    public ParteNoCoincideException() {
        super("La parte de la armadura no coincide con la del producto del catalogo.");
    }
}
