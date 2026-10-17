package nexus.dominio;

public class ReversionProductoEnConflictoException extends RuntimeException {

        public ReversionProductoEnConflictoException() {
                super("El producto cambió después de crear el respaldo. Actualiza el historial antes de revertir");
        }
}
