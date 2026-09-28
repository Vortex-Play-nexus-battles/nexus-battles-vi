package com.nexusbattles.ms_cumplimiento.auditoria.exception;

/**
 * Se lanza cuando una exportación (HU-AUD-004) pide más registros de los
 * que el sistema permite entregar en una sola operación. El criterio de
 * aceptación dice explícitamente "exige segmentar" — no se trunca ni se
 * exporta un archivo incompleto; se rechaza con esta excepción para que el
 * administrador acote el rango de fechas y vuelva a intentar.
 */
public class ExportacionExcedeMaximoException extends RuntimeException {

    private final long totalEncontrado;
    private final int maximoPermitido;

    public ExportacionExcedeMaximoException(long totalEncontrado, int maximoPermitido) {
        super("La exportación tiene " + totalEncontrado + " registros, que supera el máximo de "
            + maximoPermitido + " por operación. Acota el rango de fechas u otros filtros e inténtalo de nuevo.");
        this.totalEncontrado = totalEncontrado;
        this.maximoPermitido = maximoPermitido;
    }

    public long getTotalEncontrado() {
        return totalEncontrado;
    }

    public int getMaximoPermitido() {
        return maximoPermitido;
    }
}
