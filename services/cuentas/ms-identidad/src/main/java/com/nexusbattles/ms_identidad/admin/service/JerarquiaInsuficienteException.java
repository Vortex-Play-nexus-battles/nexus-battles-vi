package com.nexusbattles.ms_identidad.admin.service;

/**
 * La accion esta permitida para el rol de quien actua, pero no sobre esa
 * cuenta: su rol es igual o superior ({@link com.nexusbattles.ms_identidad.rbac.model.JerarquiaDeRoles}).
 * Sale como 403 {@code jerarquia-insuficiente} (AdministracionAdvice).
 */
public class JerarquiaInsuficienteException extends RuntimeException {

    public JerarquiaInsuficienteException(String detalle) {
        super(detalle);
    }
}
