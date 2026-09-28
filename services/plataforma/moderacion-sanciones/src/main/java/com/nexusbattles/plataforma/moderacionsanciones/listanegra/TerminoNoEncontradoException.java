package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

/** El termino del camino no existe (se busca por su forma normalizada). 404. */
public class TerminoNoEncontradoException extends RuntimeException {

    public TerminoNoEncontradoException(String termino) {
        super("El término '" + termino + "' no existe en la lista negra");
    }
}
