package com.nexusbattles.plataforma.correo.envio;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Identidad del remitente y enlaces publicos de los correos.
 *
 * <p><b>Por que el remitente es configuracion y no una constante.</b> Un
 * proveedor SMTP real rechaza el mensaje cuando el {@code From} no es una
 * direccion que la cuenta tiene autorizada, y ese rechazo -- silencioso para
 * quien envia -- es la causa mas comun de "el correo no llega". La direccion
 * autorizada depende del entorno y de la cuenta, asi que no puede vivir en el
 * codigo: sale de {@code MAIL_FROM}.
 *
 * <p><b>Valores por omision.</b> Los mismos que {@code application.yml}, repetidos
 * aqui para el caso que el marcador no cubre: una linea {@code MAIL_FROM=}
 * vacia en el {@code .env} (tal como viene en {@code .env.example}) llega como
 * cadena vacia, no como ausente, y anularia el valor por omision. El remitente
 * de reserva es del dominio {@code .test} (RFC 2606): no existe en Internet,
 * asi que nunca se hace pasar por nadie, y un proveedor real lo rechazara en
 * vez de entregarlo con una identidad inventada.
 *
 * @param remitente   cabecera {@code From}; acepta "Nombre &lt;correo&gt;"
 * @param responderA  cabecera {@code Reply-To}; vacio = no se pone
 * @param basePublica base de los enlaces de los correos, sin barra final
 */
@ConfigurationProperties(prefix = "correo")
public record ConfiguracionDeCorreo(String remitente, String responderA, String basePublica) {

    /** Remitente cuando MAIL_FROM no esta configurado. */
    public static final String REMITENTE_POR_OMISION = "The Nexus Battles VI <no-reply@nexusbattles.test>";

    /** Base de los enlaces cuando PUBLIC_BASE_URL no esta configurada. */
    public static final String BASE_PUBLICA_POR_OMISION = "http://localhost";

    public ConfiguracionDeCorreo {
        remitente = vacioSiNulo(remitente);
        if (remitente.isEmpty()) {
            remitente = REMITENTE_POR_OMISION;
        }
        exigirDireccionValida(remitente);
        responderA = vacioSiNulo(responderA);
        basePublica = vacioSiNulo(basePublica).replaceAll("/+$", "");
        if (basePublica.isEmpty()) {
            basePublica = BASE_PUBLICA_POR_OMISION;
        }
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

    /**
     * Un MAIL_FROM mal escrito impide arrancar, a proposito.
     *
     * <p>Si se dejara pasar, cada correo fallaria al componerse y la cola los
     * iria dando por perdidos uno a uno, en silencio. Parado en el arranque,
     * el despliegue se pone en rojo con el motivo a la vista.
     */
    private static void exigirDireccionValida(String remitente) {
        try {
            InternetAddress[] direcciones = InternetAddress.parse(remitente, true);
            if (direcciones.length != 1) {
                throw new AddressException("se esperaba una sola direccion", remitente);
            }
        } catch (AddressException e) {
            throw new IllegalArgumentException(
                    "MAIL_FROM no es una direccion de correo valida: '" + remitente + "'", e);
        }
    }
}
