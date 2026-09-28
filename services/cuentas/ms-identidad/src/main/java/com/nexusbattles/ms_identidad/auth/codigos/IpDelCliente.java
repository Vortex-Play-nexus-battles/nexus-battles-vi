package com.nexusbattles.ms_identidad.auth.codigos;

import jakarta.servlet.http.HttpServletRequest;

/**
 * La direccion del cliente para la auditoria y los avisos: el primer valor de
 * {@code X-Forwarded-For} que pone el borde, o la direccion remota si no hay.
 *
 * <p>Es informativa (auditoria, «desde donde se hizo el cambio»), nunca una
 * decision de seguridad: la cabecera la puede escribir cualquiera que llame
 * al servicio sin pasar por el borde.
 */
public final class IpDelCliente {

    private IpDelCliente() {
    }

    public static String de(HttpServletRequest peticion) {
        String reenviada = peticion.getHeader("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) {
            return reenviada.split(",")[0].trim();
        }
        return peticion.getRemoteAddr();
    }
}
