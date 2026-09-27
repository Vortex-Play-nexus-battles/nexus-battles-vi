package nexus.inventario.aplicacion;

/**
 * El catalogo describe una armadura sin la parte del cuerpo que ocupa — B4. Sin
 * ella no hay ranura donde equiparla, y la parte la decide el catalogo, no quien
 * entrega: la entrega se rechaza con 422 y no se guarda nada.
 */
public class ProductoIncompletoException extends RuntimeException {

    public ProductoIncompletoException() {
        super("El producto del catalogo es una armadura sin la parte del cuerpo que ocupa.");
    }
}
