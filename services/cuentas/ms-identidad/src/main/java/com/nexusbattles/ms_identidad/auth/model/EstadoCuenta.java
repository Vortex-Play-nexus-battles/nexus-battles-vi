package com.nexusbattles.ms_identidad.auth.model;

import java.util.List;

/**
 * Los estados de una cuenta ({@code usuarios.estado}) con el nombre que
 * publica el contrato (ms-identidad-admin.yaml, directorio y proyeccion).
 *
 * <p>Dos familias en la misma columna: el ciclo de vida de la cuenta
 * ({@link #PENDIENTE_VERIFICACION}, {@link #INACTIVO}, {@link #ACTIVO}) y la
 * proyeccion de la sancion vigente ({@link #SUSPENDIDO}, {@link #BANEADO}).
 * El panel anterior a B2 escribia la sancion en femenino (SUSPENDIDA,
 * BANEADA); V3 las normaliza y aqui se siguen reconociendo por si una fila
 * vieja se cuela, porque confundir un baneo con una cuenta activa no es un
 * riesgo aceptable.
 */
public final class EstadoCuenta {

    public static final String ACTIVO = "ACTIVO";
    /** Autorregistro sin confirmar el correo (B1): no puede iniciar sesion. */
    public static final String PENDIENTE_VERIFICACION = "PENDIENTE_VERIFICACION";
    /** Cuenta administrativa creada por un Super Administrador y aun sin activar. */
    public static final String INACTIVO = "INACTIVO";
    public static final String SUSPENDIDO = "SUSPENDIDO";
    public static final String BANEADO = "BANEADO";
    /**
     * RFINAL-05 (HU-PRV-005) — la persona ejercio el derecho al olvido y la
     * cuenta quedo anonimizada: sin datos personales y sin forma de entrar.
     * Lo escribe solo {@code AnonimizadorDeCuentas}.
     */
    public static final String ELIMINADO = "ELIMINADO";

    static final String SUSPENDIDA_ANTERIOR = "SUSPENDIDA";
    static final String BANEADA_ANTERIOR = "BANEADA";

    /**
     * HU-USR-008 — los cinco estados que publica el contrato, en el orden en
     * que los pinta el panel: los filtros del directorio y los indicadores
     * (ms-identidad-admin.yaml 1.3.0) aceptan y devuelven exactamente estos.
     */
    public static final List<String> PUBLICADOS =
            List.of(ACTIVO, PENDIENTE_VERIFICACION, INACTIVO, SUSPENDIDO, BANEADO);

    private EstadoCuenta() {
    }

    /**
     * Las formas con que puede estar guardado un estado del contrato. Filtrar
     * por SUSPENDIDO o BANEADO tiene que encontrar tambien una fila vieja en
     * femenino, por la misma razon que {@link #esBaneado} la reconoce.
     */
    public static List<String> formasGuardadas(String estado) {
        if (SUSPENDIDO.equals(estado)) {
            return List.of(SUSPENDIDO, SUSPENDIDA_ANTERIOR);
        }
        if (BANEADO.equals(estado)) {
            return List.of(BANEADO, BANEADA_ANTERIOR);
        }
        return List.of(estado);
    }

    public static boolean esBaneado(String estado) {
        return BANEADO.equals(estado) || BANEADA_ANTERIOR.equals(estado);
    }

    public static boolean esSuspendido(String estado) {
        return SUSPENDIDO.equals(estado) || SUSPENDIDA_ANTERIOR.equals(estado);
    }

    /** El estado con el nombre del contrato (las formas anteriores a B2 se traducen). */
    public static String normalizado(String estado) {
        if (esBaneado(estado)) {
            return BANEADO;
        }
        if (esSuspendido(estado)) {
            return SUSPENDIDO;
        }
        return estado;
    }
}
