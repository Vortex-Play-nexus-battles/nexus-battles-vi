package com.nexusbattles.plataforma.correo.envio;

import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Los enlaces de los correos que llevan un codigo (contrato 1.4.0).
 *
 * <p><b>El codigo va en el fragmento ({@code #codigo=...&correo=...}), nunca
 * en la consulta ({@code ?codigo=...}).</b> El navegador no envia el fragmento
 * a ningun servidor: no queda en la bitacora del borde, ni en la de un proxy,
 * ni en la cabecera {@code Referer} de la siguiente pagina. Con el codigo en la
 * consulta, cada uno de esos sitios guardaria una contrasena restablecible.
 *
 * <p>El enlace se arma al entregar, no al encolar: asi el codigo vive en un
 * solo sitio de la fila (y se borra de ahi al terminar), y un cambio de
 * {@code PUBLIC_BASE_URL} alcanza tambien a los correos que esperan reintento.
 */
@Component
public class EnlacesDeCorreo {

    private final ConfiguracionDeCorreo configuracion;

    public EnlacesDeCorreo(ConfiguracionDeCorreo configuracion) {
        this.configuracion = configuracion;
    }

    /**
     * {@code /verificar} para un autorregistro, {@code /restablecer} para una
     * cuenta creada por un Super Administrador.
     */
    public String confirmacionDeCuenta(PropositoDeConfirmacion proposito, String codigo, String correo) {
        return conCodigo(proposito.ruta(), codigo, correo);
    }

    /** Recuperacion de contrasena: siempre {@code /restablecer}. */
    public String recuperacionDeClave(String codigo, String correo) {
        return conCodigo("/restablecer", codigo, correo);
    }

    private String conCodigo(String ruta, String codigo, String correo) {
        return configuracion.enlace(ruta) + "#codigo=" + codificar(codigo) + "&correo=" + codificar(correo);
    }

    private static String codificar(String valor) {
        return URLEncoder.encode(valor == null ? "" : valor, StandardCharsets.UTF_8);
    }
}
