package com.nexusbattles.plataforma.correo.envio;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Direcciones de correo reducidas a lo justo para reconocerlas.
 *
 * <p>{@code victima@gmail.com} queda {@code v***a@gmail.com} (contrato 1.4.0,
 * {@code GET /correos/envios}). Siempre tres asteriscos, sea cual sea el largo
 * del usuario: si la mascara tuviera un asterisco por letra, contaria cuantas
 * tiene la direccion, que es media pista para adivinarla. El dominio se
 * conserva entero porque es el dato que sirve para diagnosticar entregas: casi
 * todo lo que un proveedor rechaza lo rechaza por dominio.
 *
 * <p>Es lo unico que sale de este servicio sobre un destinatario, sea hacia la
 * bitacora o hacia la evidencia de entrega: ninguna de las dos es una lista de
 * correos de los jugadores.
 */
public final class Enmascarar {

    private static final String ASTERISCOS = "***";

    /** Cualquier cosa con forma de direccion dentro de un texto libre. */
    private static final Pattern DIRECCION = Pattern.compile("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+");

    private Enmascarar() {}

    /** Una direccion, p. ej. {@code simon.perez@gmail.com} -> {@code s***z@gmail.com}. */
    public static String direccion(String direccion) {
        if (direccion == null || direccion.isBlank()) {
            return "(sin destinatario)";
        }
        String limpia = direccion.trim();
        int arroba = limpia.lastIndexOf('@');
        if (arroba < 1) {
            return ASTERISCOS;
        }
        String usuario = limpia.substring(0, arroba);
        String dominio = limpia.substring(arroba);
        if (usuario.length() <= 2) {
            // Con una o dos letras, la primera y la ultima SON el usuario.
            return ASTERISCOS + dominio;
        }
        return usuario.charAt(0) + ASTERISCOS + usuario.charAt(usuario.length() - 1) + dominio;
    }

    /**
     * El mismo texto con cada direccion enmascarada.
     *
     * <p>Para los mensajes de error del SMTP, que suelen citar al destinatario
     * ({@code 550 5.1.1 <x@y>: Recipient address rejected}) y acaban en la
     * bitacora y en la columna {@code ultimo_error}.
     */
    public static String direccionesEn(String texto) {
        if (texto == null || texto.isEmpty()) {
            return texto;
        }
        Matcher encontrada = DIRECCION.matcher(texto);
        StringBuilder salida = new StringBuilder();
        while (encontrada.find()) {
            encontrada.appendReplacement(salida, Matcher.quoteReplacement(direccion(encontrada.group())));
        }
        encontrada.appendTail(salida);
        return salida.toString();
    }
}
