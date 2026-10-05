package com.nexusbattles.ms_identidad.admin.directorio;

/**
 * Los filtros dejan mas cuentas de las que caben en una exportacion — HU-USR-008.
 *
 * <p>422 {@code exportacion-demasiado-grande} (ms-identidad-admin.yaml 1.3.0):
 * no se entrega un archivo recortado, que se leeria como el listado completo.
 * El mensaje dice las dos cifras para que quien exporta sepa cuanto acotar.
 */
public class ExportacionDemasiadoGrandeException extends RuntimeException {

    public ExportacionDemasiadoGrandeException(long cuentas, int maximo) {
        super("Los filtros dejan " + cuentas + " cuentas y una exportación admite como máximo "
                + maximo + ". Acota la búsqueda (rol, estado o fechas de registro) y vuelve a exportar.");
    }
}
