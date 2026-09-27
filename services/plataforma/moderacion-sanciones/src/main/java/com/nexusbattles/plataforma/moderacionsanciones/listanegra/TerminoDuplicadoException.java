package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

/**
 * Ya hay un termino con la misma forma normalizada: «Spider-Man» y «spiderman»
 * son el mismo termino. 409 (moderacion-lista-negra.yaml 2.0.x).
 */
public class TerminoDuplicadoException extends RuntimeException {

    public TerminoDuplicadoException(String termino, String normalizado) {
        super("Ya existe un término que se compara igual que '" + termino + "' (forma normalizada '"
                + normalizado + "')");
    }
}
