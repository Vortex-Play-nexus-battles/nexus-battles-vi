package com.nexusbattles.ms_identidad.auth.segundofactor;

import com.nexusbattles.ms_identidad.auth.segundofactor.DesafioDeAcceso.Proposito;

import java.time.Instant;

/**
 * Un desafio recien emitido: el valor en claro (solo viaja en la respuesta del
 * login; la base guarda su resumen), cuando caduca y para que sirve.
 */
public record DesafioEmitido(String valor, Instant expiraEn, Proposito proposito) {

    @Override
    public String toString() {
        return "DesafioEmitido[valor=********, expiraEn=" + expiraEn + ", proposito=" + proposito + "]";
    }
}
