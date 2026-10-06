package nexus.dominio;

public class RespaldoProductoNoEncontradoException extends RuntimeException {

        public RespaldoProductoNoEncontradoException() {
                super("El respaldo no existe o no corresponde al producto indicado");
        }
}
