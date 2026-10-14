package com.nexusbattles.plataforma.adminparametros.parametros;

/**
 * Deja constancia de un cambio en la bitacora JSON (regla 6), dentro de la
 * peticion del administrador. La bitacora central de ms-cumplimiento la
 * alimenta {@link EnvioDeAuditoria} desde la propia version (SA-5): aqui no
 * se llama a ningun servicio.
 */
public interface Auditoria {

    void registrar(Version version);
}
