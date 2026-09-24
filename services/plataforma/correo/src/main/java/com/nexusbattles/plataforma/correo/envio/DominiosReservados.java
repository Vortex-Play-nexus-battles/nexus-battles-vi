package com.nexusbattles.plataforma.correo.envio;

import java.util.Locale;
import java.util.Set;

/**
 * Dominios que ningun correo puede alcanzar, por norma.
 *
 * <p>RFC 2606 reserva {@code .test}, {@code .example}, {@code .invalid} y
 * {@code .localhost}, y los tres {@code example.com/.net/.org}, justo para que
 * las pruebas tengan direcciones que no pertenecen a nadie. RFC 6761 lo
 * confirma, y RFC 6762 reserva {@code .local} para la red local.
 *
 * <p><b>Por que importa.</b> Las pruebas automaticas (canarios, smoke) crean
 * cuentas {@code @nexus.test} en cada despliegue. Mientras el correo salia a
 * Mailpit daba igual. Con un proveedor real, cada una de esas cuentas se
 * convierte en un correo que el proveedor acepta, no puede entregar y
 * devuelve rebotado a la bandeja del remitente: cuota diaria gastada en
 * nada, la bandeja del equipo llena de rebotes, y un remitente que envia
 * mucho a dominios inexistentes es exactamente lo que los filtros de correo
 * no deseado castigan. Medido en dev a los cinco minutos del cambio: 5 de 7
 * correos iban a {@code nexus.test}.
 */
final class DominiosReservados {

    private static final Set<String> TLD_RESERVADOS =
            Set.of("test", "example", "invalid", "localhost", "local");

    private static final Set<String> DOMINIOS_RESERVADOS =
            Set.of("example.com", "example.net", "example.org");

    private DominiosReservados() {}

    /** True si la direccion no puede llegar a ninguna bandeja real. */
    static boolean esReservado(String direccion) {
        if (direccion == null) {
            return false;
        }
        int arroba = direccion.lastIndexOf('@');
        if (arroba < 0 || arroba == direccion.length() - 1) {
            return false;
        }
        String dominio = direccion.substring(arroba + 1).trim().toLowerCase(Locale.ROOT);
        if (dominio.endsWith(".")) {
            dominio = dominio.substring(0, dominio.length() - 1);
        }
        if (DOMINIOS_RESERVADOS.contains(dominio)) {
            return true;
        }
        for (String reservado : DOMINIOS_RESERVADOS) {
            if (dominio.endsWith("." + reservado)) {
                return true;
            }
        }
        int punto = dominio.lastIndexOf('.');
        String tld = punto < 0 ? dominio : dominio.substring(punto + 1);
        return TLD_RESERVADOS.contains(tld);
    }
}