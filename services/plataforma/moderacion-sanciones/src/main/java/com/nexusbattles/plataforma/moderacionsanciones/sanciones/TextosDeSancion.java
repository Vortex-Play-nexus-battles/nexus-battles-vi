package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Lo que dice el aviso dentro de la aplicacion — §7.3.7 «Cada notificacion
 * debe incluir»: la accion tomada, el motivo especifico, la politica violada,
 * el proceso de apelacion y la fecha de vigencia (SA-4).
 *
 * <p>Hasta SA-4 los avisos salian sin tildes y con la fecha cruda
 * ({@code 2026-10-16T04:02Z}): el jugador leia la hora de un servidor, no la
 * suya. Ahora las fechas van en la hora de Colombia, escritas como se leen.
 */
final class TextosDeSancion {

    private static final Locale ESPANOL = Locale.forLanguageTag("es-CO");

    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, h:mm a", ESPANOL);

    private TextosDeSancion() {
    }

    /**
     * «16 de octubre de 2026, 4:02 p. m.», en la hora de Colombia. Los datos
     * de idioma del JDK separan «p.» de «m.» con un espacio fino de no
     * separacion (U+202F) que algunos clientes de correo pintan como un
     * cuadro: se escribe con un espacio normal.
     */
    static String fecha(OffsetDateTime instante) {
        return FECHA.format(instante.atZoneSameInstant(PlazoDeDecision.ZONA)).replace('\u202F', ' ')
                .replace('\u00A0', ' ');
    }

    static String nombreDe(Sancion.Tipo tipo) {
        return switch (tipo) {
            case ADVERTENCIA -> "advertencia";
            case SUSPENSION -> "suspensión";
            case BANEO -> "inhabilitación definitiva";
        };
    }

    static String tituloDeLaEmision(Sancion sancion) {
        return switch (sancion.tipo()) {
            case ADVERTENCIA -> "Recibiste una advertencia";
            case SUSPENSION -> "Tu cuenta está suspendida hasta el " + fecha(sancion.vigenteHasta());
            case BANEO -> "Tu cuenta fue inhabilitada de forma definitiva";
        };
    }

    /**
     * La causal, el motivo y la politica; que significa la sancion; y hasta
     * cuando y como se apela.
     */
    static String cuerpoDeLaEmision(Sancion sancion, OffsetDateTime apelableHasta) {
        StringBuilder cuerpo = new StringBuilder();
        if (sancion.causal() != null) {
            cuerpo.append("Causal: ").append(sancion.causal().etiqueta()).append(". ");
        }
        cuerpo.append("Motivo: ").append(conPunto(sancion.motivo()));
        if (sancion.politica() != null) {
            cuerpo.append(" Política: ").append(conPunto(sancion.politica()));
        }
        cuerpo.append(' ').append(switch (sancion.tipo()) {
            case ADVERTENCIA -> "La advertencia queda registrada y no restringe tu acceso.";
            case SUSPENSION -> "No podrás entrar hasta el " + fecha(sancion.vigenteHasta())
                    + "; tu inventario y tu progreso se mantienen intactos.";
            case BANEO -> "Pierdes el acceso a todos los servicios y tu inventario queda congelado.";
        });
        cuerpo.append(" Puedes apelar hasta el ").append(fecha(apelableHasta));
        // Solo quien conserva el acceso llega a «Mis sanciones». La suspension
        // y el baneo cierran la sesion: se apela desde la entrada, con la
        // contrasena (ms-identidad-auth 2.4.0, apelacion sin sesion).
        cuerpo.append(sancion.tipo() == Sancion.Tipo.ADVERTENCIA
                ? " desde «Mis sanciones», en el menú de tu cuenta"
                : " desde la pantalla de entrada: escribe tu correo y tu contraseña y elige «Apelar esta sanción»");
        cuerpo.append("; el panel te responderá en un plazo de ").append(PlazoDeDecision.DIAS_HABILES)
                .append(" días hábiles.");
        return cuerpo.toString();
    }

    static String tituloDeLaApelacionRecibida() {
        return "Recibimos tu apelación";
    }

    static String cuerpoDeLaApelacionRecibida(Sancion sancion, OffsetDateTime decisionAntesDe) {
        return "Un panel de moderadores y administradores revisará tu apelación sobre tu "
                + nombreDe(sancion.tipo()) + " y te notificará su decisión a más tardar el "
                + conPunto(fecha(decisionAntesDe));
    }

    static String tituloDeLaResolucion(Apelacion.Estado decision) {
        return switch (decision) {
            case MANTENIDA -> "Tu apelación fue resuelta: la sanción se mantiene";
            case REDUCIDA -> "Tu apelación fue resuelta: la sanción se redujo";
            case REVERTIDA -> "Tu apelación fue resuelta: la sanción se revirtió";
            case PENDIENTE -> "Tu apelación sigue en revisión";
        };
    }

    static String cuerpoDeLaResolucion(Sancion sancion, Apelacion.Estado decision, String motivo,
                                       OffsetDateTime nuevaVigencia) {
        String cuerpo = "Decisión del panel sobre tu " + nombreDe(sancion.tipo()) + ": " + conPunto(motivo);
        if (decision == Apelacion.Estado.REDUCIDA && nuevaVigencia != null) {
            cuerpo += " Tu suspensión termina ahora el " + conPunto(fecha(nuevaVigencia));
        }
        return cuerpo;
    }

    static String tituloDelLevantamiento(Sancion sancion) {
        return "Tu " + nombreDe(sancion.tipo()) + " fue levantada";
    }

    static String cuerpoDelLevantamiento(Sancion sancion, String motivo) {
        return "Un administrador levantó tu " + nombreDe(sancion.tipo()) + ". Motivo: " + conPunto(motivo);
    }

    /**
     * El texto como final de frase: con su punto, sin duplicarlo cuando ya lo
     * trae (las horas en es-CO acaban en «a. m.» o «p. m.»).
     */
    private static String conPunto(String texto) {
        String limpio = texto.strip();
        return limpio.endsWith(".") || limpio.endsWith("!") || limpio.endsWith("?") ? limpio : limpio + ".";
    }
}
