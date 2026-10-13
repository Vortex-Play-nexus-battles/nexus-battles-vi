package nexus.dominio;

public class BannerNoEncontradoException extends RuntimeException {

        public BannerNoEncontradoException(String id) {
                super("No existe un banner con el identificador " + id);
        }
}
