package com.nexusbattles.ms_ecommerce.traza;

import org.slf4j.MDC;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Contexto de traza W3C de la tienda (regla 5 de plataforma).
 *
 * <p>Una compra habla con seis servicios (productos, inventario, finanzas,
 * identidad, correo, admin-parametros) y puede terminarla, minutos despues, la
 * tarea programada. Para seguirla entera en la bitacora de cualquiera de ellos,
 * todas esas llamadas llevan el mismo trace-id: el de la peticion que la
 * empezo si el cliente mando {@code traceparent}, uno nuevo si no. La orden lo
 * guarda ({@code ordenes.traza}) y la tarea programada lo reabre al
 * retomarla.
 *
 * <p>Es la misma clase que {@code Traza} del alta de ms-identidad: un
 * {@link ThreadLocal} (una compra se procesa entera en un hilo) que
 * {@link #cerrar()} limpia siempre en un {@code finally}, y la clave
 * {@value #CLAVE_MDC} en el MDC para que cada linea de la bitacora la lleve.
 */
public final class Traza {

    /** Clave del MDC: la bitacora JSON la incluye en cada linea. */
    public static final String CLAVE_MDC = "traceId";

    private static final ThreadLocal<String> ACTUAL = new ThreadLocal<>();
    private static final Pattern TRACEPARENT =
            Pattern.compile("^00-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$");
    private static final Pattern TRACE_ID = Pattern.compile("[0-9a-f]{32}");
    private static final String NULO = "00000000000000000000000000000000";
    private static final SecureRandom AZAR = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

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

    /** Fija el trace-id del hilo actual; uno nuevo si el que se pasa no es valido. */
    public static String abrir(String traceId) {
        String efectivo = traceId != null && TRACE_ID.matcher(traceId).matches() && !NULO.equals(traceId)
                ? traceId : nuevoTraceId();
        ACTUAL.set(efectivo);
        MDC.put(CLAVE_MDC, efectivo);
        return efectivo;
    }

    /** El trace-id del hilo, o vacio si nadie lo abrio. */
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

    static String nuevoTraceId() {
        byte[] bytes = new byte[16];
        AZAR.nextBytes(bytes);
        return HEX.formatHex(bytes);
    }

    private static String nuevoSpanId() {
        byte[] bytes = new byte[8];
        AZAR.nextBytes(bytes);
        return HEX.formatHex(bytes);
    }
}
