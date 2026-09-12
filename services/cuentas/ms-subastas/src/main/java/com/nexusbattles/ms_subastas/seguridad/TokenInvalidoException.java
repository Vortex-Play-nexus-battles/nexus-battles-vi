package com.nexusbattles.ms_subastas.seguridad;

/**
 * El token no acredita a nadie: falta, no esta firmado con nuestra clave, ya
 * expiro, o no trae los datos que ms-identidad siempre emite.
 *
 * <p>El motivo esta redactado para poder mostrarse a quien llama. La causa
 * original de la libreria se conserva para la bitacora, pero no se expone en el
 * mensaje: el detalle de por que falla un token es informacion util para quien
 * intenta falsificarlo.
 */
public class TokenInvalidoException extends RuntimeException {

    public TokenInvalidoException(String motivo) {
        super(motivo);
    }

    public TokenInvalidoException(String motivo, Throwable causa) {
        super(motivo, causa);
    }
}
