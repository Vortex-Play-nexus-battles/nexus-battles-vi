package com.nexusbattles.ms_subastas.pujas.service;

import java.util.UUID;

public class SubastaNoEncontradaException extends RuntimeException {

    public SubastaNoEncontradaException(UUID subastaId) {
        super("No existe la subasta " + subastaId);
    }
}
