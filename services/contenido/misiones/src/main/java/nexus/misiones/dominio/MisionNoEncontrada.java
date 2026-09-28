package nexus.misiones.dominio;

/** No hay una mision publicada con ese identificador (404). */
public class MisionNoEncontrada extends RuntimeException {

    public MisionNoEncontrada(String id) {
        super("No hay una misión publicada con el identificador «" + id + "».");
    }
}
