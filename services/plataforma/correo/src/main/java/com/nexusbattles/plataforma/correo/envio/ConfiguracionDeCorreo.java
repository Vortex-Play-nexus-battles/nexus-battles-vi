package com.nexusbattles.plataforma.correo.envio;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Identidad del remitente y enlaces publicos de los correos.
 *
 * <p><b>Por que el remitente es configuracion y no una constante.</b> Un
 * proveedor SMTP real rechaza el mensaje cuando el {@code From} no es una
 * direccion que la cuenta tiene autorizada, y ese rechazo -- silencioso para
 * quien envia -- es la causa mas comun de "el correo no llega". La direccion
 * autorizada depende del entorno y de la cuenta, asi que no puede vivir en el
 * codigo.
 *
 * @param remitente        cabecera {@code From}; acepta "Nombre &lt;correo&gt;"
 * @param responderA       cabecera {@code Reply-To}; vacio = no se pone
 * @param basePublica      base de los enlaces de los correos, sin barra final
 * @param enviosRecordados cuantos envios recientes se guardan en memoria
 */
@ConfigurationProperties(prefix = "correo")
public record ConfiguracionDeCorreo(
        String remitente, String responderA, String basePublica, Integer enviosRecordados) {

    public ConfiguracionDeCorreo {
        remitente = vacioSiNulo(remitente);
        responderA = vacioSiNulo(responderA);
        basePublica = vacioSiNulo(basePublica).replaceAll("/+$", "");
        enviosRecordados = enviosRecordados == null || enviosRecordados < 1 ? 200 : enviosRecordados;
    }

    /** True cuando hay un remitente que poner; vacio deja que decida el servidor. */
    public boolean tieneRemitente() {
        return !remitente.isBlank();
    }

    public boolean tieneResponderA() {
        return !responderA.isBlank();
    }

    /** Enlace publico a una ruta de la aplicacion. */
    public String enlace(String ruta) {
        String limpia = ruta == null ? "" : ruta.trim();
        if (limpia.isEmpty()) {
            return basePublica;
        }
        return basePublica + (limpia.startsWith("/") ? limpia : "/" + limpia);
    }

    private static String vacioSiNulo(String valor) {
        return valor == null ? "" : valor.trim();
    }
}