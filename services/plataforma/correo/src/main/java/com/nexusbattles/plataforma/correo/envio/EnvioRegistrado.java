package com.nexusbattles.plataforma.correo.envio;

import java.time.Instant;

/**
 * Que paso con UN envio, en las palabras que permiten demostrarlo.
 *
 * <p>RF-COR-001 exige "evidencia de entrega efectiva del correo". Un 202 del
 * controlador no es esa evidencia: dice que la peticion se acepto. Esto dice
 * que el servidor SMTP acepto el mensaje, con que identificador y cuando --
 * que es lo que se puede contrastar contra los registros del proveedor.
 *
 * <p><b>Lo que NO guarda:</b> el cuerpo del mensaje, el codigo de
 * recuperacion, el nombre de nadie, ni la direccion completa. El destinatario
 * viaja enmascarado: basta para reconocer de quien se habla y no convierte
 * este registro en una lista de correos.
 *
 * @param instante      cuando se intento
 * @param destinatario  direccion enmascarada, p. ej. {@code si***n@gmail.com}
 * @param plantilla     que correo era
 * @param estado        ACEPTADO o RECHAZADO
 * @param identificador Message-ID que puso el servidor, si lo hubo
 * @param motivo        por que fue rechazado; vacio cuando se acepto
 */
public record EnvioRegistrado(
        Instant instante,
        String destinatario,
        String plantilla,
        String estado,
        String identificador,
        String motivo) {

    public static final String ACEPTADO = "ACEPTADO";
    public static final String RECHAZADO = "RECHAZADO";

    /**
     * Enmascara una direccion dejando lo justo para reconocerla.
     *
     * <p>{@code simon.perez@gmail.com} queda {@code si*********z@gmail.com}.
     * El dominio se conserva entero porque es el dato que sirve para
     * diagnosticar entregas: casi todo lo que se rechaza se rechaza por
     * dominio.
     */
    public static String enmascarar(String direccion) {
        if (direccion == null || direccion.isBlank()) {
            return "(sin destinatario)";
        }
        int arroba = direccion.indexOf('@');
        if (arroba < 1) {
            return "***";
        }
        String usuario = direccion.substring(0, arroba);
        String dominio = direccion.substring(arroba);
        if (usuario.length() <= 2) {
            return "*".repeat(usuario.length()) + dominio;
        }
        return usuario.charAt(0)
                + "*".repeat(usuario.length() - 2)
                + usuario.charAt(usuario.length() - 1)
                + dominio;
    }

    public static EnvioRegistrado aceptado(
            Instant instante, String direccion, String plantilla, String identificador) {
        return new EnvioRegistrado(
                instante,
                enmascarar(direccion),
                plantilla,
                ACEPTADO,
                identificador == null ? "" : identificador,
                "");
    }

    public static EnvioRegistrado rechazado(
            Instant instante, String direccion, String plantilla, String motivo) {
        return new EnvioRegistrado(
                instante,
                enmascarar(direccion),
                plantilla,
                RECHAZADO,
                "",
                motivo == null ? "" : motivo);
    }
}