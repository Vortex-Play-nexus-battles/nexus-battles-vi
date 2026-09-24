package com.nexusbattles.plataforma.correo.envio;

import java.time.Instant;

/**
 * Que paso con UN envio, en las palabras que permiten demostrarlo.
 *
 * <p>RF-COR-001 exige "evidencia de entrega efectiva del correo". Un 202 del
 * controlador no es esa evidencia: dice que la peticion se acepto. Esto dice
 * que el servidor SMTP acepto el mensaje, con que identificador, cuando y
 * <b>a que servidor</b> -- que es lo que se puede contrastar contra los
 * registros del proveedor.
 *
 * <p><b>Lo que NO guarda:</b> el cuerpo del mensaje, el codigo de
 * recuperacion, el nombre de nadie, ni la direccion completa. El destinatario
 * viaja enmascarado: basta para reconocer de quien se habla y no convierte
 * este registro en una lista de correos.
 *
 * @param instante      cuando se intento
 * @param destinatario  direccion enmascarada, p. ej. {@code si***n@gmail.com}
 * @param plantilla     que correo era
 * @param estado        ACEPTADO, RECHAZADO u OMITIDO
 * @param destino       PROVEEDOR o BUZON_DE_PRUEBAS; vacio si se omitio
 * @param identificador Message-ID que puso el servidor, si lo hubo
 * @param motivo        por que fue rechazado u omitido; vacio si se acepto
 */
public record EnvioRegistrado(
        Instant instante,
        String destinatario,
        String plantilla,
        String estado,
        String destino,
        String identificador,
        String motivo) {

    public static final String ACEPTADO = "ACEPTADO";
    public static final String RECHAZADO = "RECHAZADO";
    public static final String OMITIDO = "OMITIDO";

    public static final String PROVEEDOR = "PROVEEDOR";
    public static final String BUZON_DE_PRUEBAS = "BUZON_DE_PRUEBAS";

    /**
     * Enmascara una direccion dejando lo justo para reconocerla.
     *
     * <p>{@code simon.perez@gmail.com} queda {@code s*********z@gmail.com}.
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
        return aceptado(instante, direccion, plantilla, identificador, PROVEEDOR);
    }

    public static EnvioRegistrado aceptado(
            Instant instante,
            String direccion,
            String plantilla,
            String identificador,
            String destino) {
        return new EnvioRegistrado(
                instante,
                enmascarar(direccion),
                plantilla,
                ACEPTADO,
                destino,
                identificador == null ? "" : identificador,
                "");
    }

    public static EnvioRegistrado rechazado(
            Instant instante, String direccion, String plantilla, String motivo) {
        return rechazado(instante, direccion, plantilla, motivo, PROVEEDOR);
    }

    public static EnvioRegistrado rechazado(
            Instant instante, String direccion, String plantilla, String motivo, String destino) {
        return new EnvioRegistrado(
                instante,
                enmascarar(direccion),
                plantilla,
                RECHAZADO,
                destino,
                "",
                motivo == null ? "" : motivo);
    }

    /** No se envio a proposito: direccion reservada y sin buzon de pruebas. */
    public static EnvioRegistrado omitido(Instant instante, String direccion, String plantilla) {
        return new EnvioRegistrado(
                instante,
                enmascarar(direccion),
                plantilla,
                OMITIDO,
                "",
                "",
                "dominio reservado para pruebas (RFC 2606): no puede llegar a ninguna bandeja");
    }
}