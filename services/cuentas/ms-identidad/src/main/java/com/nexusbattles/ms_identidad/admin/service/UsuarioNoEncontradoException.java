package com.nexusbattles.ms_identidad.admin.service;

/**
 * No existe la cuenta pedida por su identificador interno. Extiende
 * {@link IllegalStateException} para que las rutas del panel que ya la
 * traducian a 404 sigan igual; lo nuevo es que un fallo de auditoria ya no se
 * confunde con ella (antes los dos eran {@code IllegalStateException} y una
 * bitacora caida salia como «404 no existe el usuario»).
 */
public class UsuarioNoEncontradoException extends IllegalStateException {

    public UsuarioNoEncontradoException(Long usuarioId) {
        super("No existe el usuario " + usuarioId);
    }
}
