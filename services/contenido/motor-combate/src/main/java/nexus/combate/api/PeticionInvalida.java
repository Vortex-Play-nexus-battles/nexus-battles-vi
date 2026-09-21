package nexus.combate.api;

/**
 * La peticion no se puede interpretar — 400.
 *
 * <p>Distinta de las excepciones del dominio: aqui se recogen los errores de
 * traduccion del JSON —un prototipo que no existe, un contexto a medias— antes
 * de que nada llegue a las reglas del juego. Las reglas tienen sus propias
 * excepciones y no hay que envolverlas.
 */
public class PeticionInvalida extends RuntimeException {

    public PeticionInvalida(String mensaje) {
        super(mensaje);
    }
}
