package com.nexusbattles.ms_identidad.rbac.service;

/**
 * Quitar el rol al ultimo Super Administrador dejaria el sistema sin nadie que
 * pueda crear administradores ni cambiar roles (HU-RBAC-003). 409
 * {@code ultimo-super-administrador}; antes salia como 500.
 */
public class UltimoSuperAdministradorException extends IllegalStateException {

    public UltimoSuperAdministradorException() {
        super("No se puede quitar el rol Super Administrador: es el único que queda en el sistema.");
    }
}
