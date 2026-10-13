package com.nexusbattles.ms_identidad.auth.segundofactor;

import com.nexusbattles.ms_identidad.auth.segundofactor.DesafioDeAcceso.Proposito;

/**
 * La contrasena es correcta pero falta el segundo paso (HU-AUT-007): el login
 * no emite la sesion y responde 403 con el desafio
 * ({@code segundo-factor-requerido} o {@code segundo-factor-enrolamiento-requerido},
 * ms-identidad-auth.yaml 2.2.0).
 *
 * <p>Es un 403 y no un 200 a proposito: un cliente anterior que no conoce el
 * desafio lo trata como un rechazo —y enseña el {@code detail}—, nunca como
 * una sesion.
 */
public class SegundoFactorRequeridoException extends RuntimeException {

    private final DesafioEmitido desafio;

    public SegundoFactorRequeridoException(DesafioEmitido desafio) {
        super(desafio.proposito() == Proposito.ENROLAR
                ? "Tu rol exige la verificación en dos pasos y tu cuenta aún no la tiene. Actívala para terminar de "
                        + "entrar."
                : "Esta cuenta tiene activada la verificación en dos pasos. Escribe el código de tu aplicación de "
                        + "autenticación para terminar de entrar.");
        this.desafio = desafio;
    }

    public DesafioEmitido getDesafio() {
        return desafio;
    }

    /** El {@code type} del contrato, sin el prefijo comun. */
    public String tipo() {
        return desafio.proposito() == Proposito.ENROLAR
                ? "segundo-factor-enrolamiento-requerido"
                : "segundo-factor-requerido";
    }

    public String titulo() {
        return desafio.proposito() == Proposito.ENROLAR
                ? "Falta activar el segundo factor"
                : "Falta el segundo factor";
    }
}
