package com.nexusbattles.ms_identidad.sanciones.apelacion;

/**
 * 401 {@code codigo-de-apelacion-invalido}: el codigo no existe, caduco, ya
 * se uso o la cuenta cambio de version de token (contrasena o rol) desde que
 * se emitio. Siempre el mismo mensaje: el motivo exacto no se le dice a quien
 * prueba codigos.
 */
public class CodigoDeApelacionInvalidoException extends RuntimeException {

    public static final String MENSAJE =
            "El código para apelar ya no sirve. Vuelve a iniciar sesión con tu contraseña para obtener otro.";

    public CodigoDeApelacionInvalidoException() {
        super(MENSAJE);
    }
}
