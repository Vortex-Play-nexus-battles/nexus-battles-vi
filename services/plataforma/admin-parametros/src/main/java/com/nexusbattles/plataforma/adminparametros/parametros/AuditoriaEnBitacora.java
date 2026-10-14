package com.nexusbattles.plataforma.adminparametros.parametros;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Cada cambio en la bitacora JSON (regla 6); la entrega a ms-cumplimiento la hace {@link EnvioDeAuditoria}. */
public class AuditoriaEnBitacora implements Auditoria {

    private static final Logger BITACORA = LoggerFactory.getLogger(AuditoriaEnBitacora.class);

    @Override
    public void registrar(Version v) {
        BITACORA.info("AUDITORIA_PARAMETRO clave={} version={} de={} a={} por={} desde={} motivo={}",
                v.clave(), v.version(), v.valorAnterior(), v.valorNuevo(), v.cambiadoPor(), v.ipOrigen(), v.motivo());
    }
}
