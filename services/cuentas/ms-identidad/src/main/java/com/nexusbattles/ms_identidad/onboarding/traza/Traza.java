package com.nexusbattles.ms_identidad.onboarding.traza;

import org.slf4j.MDC;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Contexto de traza W3C del alta de un jugador (regla 5 de plataforma).
 *
 * <p>Todos los intentos del alta de una misma cuenta comparten un trace-id,
 * guardado en {@code onboarding_jugador.traza}: el del registro si el cliente
 * mando {@code traceparent}, uno nuevo si no. Cada llamada saliente
 * (ms-finanzas, inventario, productos, admin-parametros) lleva ese trace-id
 * con un span nuevo, asi que en la bitacora de cualquiera de esos servicios se
 * puede seguir el alta entera: registro, creditos, heroe y equipo.
 *
 * <p>Es un {@link ThreadLocal} porque un alta se procesa entera en un hilo;
 * {@link #cerrar()} lo limpia siempre en un {@code finally}.
 */
public final class Traza {

    private static final ThreadLocal<String> ACTUAL = new ThreadLocal<>();
    private static final Pattern TRACEPARENT =
            Pattern.compile("^00-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$");
    private static final String NULO = "00000000000000000000000000000000";
    private static final SecureRandom AZAR = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    /** Clave del MDC: la bitacora de identidad la puede incluir en cada linea. */
    public static final String CLAVE_MDC = "traceId";

    private Traza() {
    }

    /** El trace-id de una cabecera {@code traceparent} valida, o vacio si no lo es. */
    public static Optional<String> traceIdDe(String traceparent) {
        if (traceparent == null) {
            return Optional.empty();
        }
        Matcher m = TRACEPARENT.matcher(traceparent.trim().toLowerCase());
        if (!m.matches() || NULO.equals(m.group(1))) {
            return Optional.empty();
        }
        return Optional.of(m.group(1));
    }

    public static String nuevoTraceId() {
        byte[] bytes = new byte[16];
        AZAR.nextBytes(bytes);
        return HEX.formatHex(bytes);
    }

    static String nuevoSpanId() {
        byte[] bytes = new byte[8];
        AZAR.nextBytes(bytes);
        return HEX.formatHex(bytes);
    }

    /** Fija el trace-id del hilo actual; uno nuevo si el que se pasa no es valido. */
    public static String abrir(String traceId) {
        String efectivo = traceId != null && traceId.matches("[0-9a-f]{32}") && !NULO.equals(traceId)
                ? traceId : nuevoTraceId();
        ACTUAL.set(efectivo);
        MDC.put(CLAVE_MDC, efectivo);
        return efectivo;
    }

    public static Optional<String> actual() {
        return Optional.ofNullable(ACTUAL.get());
    }

    /** Cabecera {@code traceparent} para una llamada saliente: mismo trace, span nuevo. */
    public static Optional<String> traceparentHijo() {
        return actual().map(traceId -> "00-" + traceId + "-" + nuevoSpanId() + "-01");
    }

    public static void cerrar() {
        ACTUAL.remove();
        MDC.remove(CLAVE_MDC);
    }
}
