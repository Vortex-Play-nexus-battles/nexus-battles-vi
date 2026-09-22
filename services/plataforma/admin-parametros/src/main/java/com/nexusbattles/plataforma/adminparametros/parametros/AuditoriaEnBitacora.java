package com.nexusbattles.plataforma.adminparametros.parametros;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Sin AUDITORIA_URL: cada cambio queda al menos en la bitacora JSON (regla 6). */
public class AuditoriaEnBitacora implements Auditoria {

    private static final Logger BITACORA = LoggerFactory.getLogger(AuditoriaEnBitacora.class);

    @Override
    public void registrar(Version v) {
        BITACORA.info("Parametro cambiado (sin auditoria externa configurada): clave={} version={} de={} a={} por={} motivo={}",
                v.clave(), v.version(), v.valorAnterior(), v.valorNuevo(), v.cambiadoPor(), v.motivo());
    }
}
