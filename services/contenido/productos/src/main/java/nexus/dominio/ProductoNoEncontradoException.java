package nexus.dominio;

public class ProductoNoEncontradoException extends RuntimeException {

        public ProductoNoEncontradoException() {
                super("El producto solicitado no existe en el catálogo.");
        }
}
