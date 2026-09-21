package com.nexusbattles.plataforma.adminparametros.parametros;

/**
 * Puerto hacia la auditoria de ms-cumplimiento (HU-AUD-001). Fail-open: si
 * no responde, el cambio se aplica igual (el historial local es la
 * evidencia) y se anota en la bitacora.
 */
public interface Auditoria {

    void registrar(Version version);
}
