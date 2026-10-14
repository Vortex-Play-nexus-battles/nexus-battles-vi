package com.nexusbattles.ms_identidad.auditoria.client;

/**
 * ms-cumplimiento no registro un evento que la operacion exige auditar
 * (fail-closed, HU-AUD-001): la operacion se deshace y la ruta responde 503
 * {@code auditoria-no-disponible} (AdministracionAdvice), con
 * {@code Retry-After}. Es un fallo transitorio de otro servicio, no un dato
 * invalido de quien actua.
 */
public class AuditoriaNoDisponibleException extends RuntimeException {

    public static final int REINTENTAR_EN_SEGUNDOS = 30;

    public AuditoriaNoDisponibleException(String detalle) {
        super(detalle);
    }
}
