package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

public class TerminoNoEncontradoException extends RuntimeException {

    public TerminoNoEncontradoException(String termino) {
        super("El termino '" + termino + "' no existe en la lista negra");
    }
}
