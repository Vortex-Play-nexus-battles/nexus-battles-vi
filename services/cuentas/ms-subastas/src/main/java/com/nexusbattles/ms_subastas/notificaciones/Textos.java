package com.nexusbattles.ms_subastas.notificaciones;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Como se dicen en un aviso las cifras y los jugadores.
 */
public final class Textos {

    private Textos() {
    }

    /**
     * Creditos sin ceros de mas: 150 y no 150.00, pero 1.50 y no 1.5 (las
     * penalizaciones de cancelar llevan centimos).
     */
    public static String creditos(BigDecimal monto) {
        if (monto == null) {
            return "0";
        }
        BigDecimal limpio = monto.stripTrailingZeros();
        if (limpio.scale() <= 0) {
            return limpio.setScale(0, RoundingMode.UNNECESSARY).toPlainString();
        }
        return monto.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * El apodo anonimizado parcialmente (7.7.9 y 7.7.11: «Usuario que realizo
     * cada puja (anonimizado parcialmente)», «Anonimizacion parcial de usuarios
     * en historial de pujas»): primera y ultima letra. Sin apodo conocido —pujas
     * anteriores a B8—, «Un jugador».
     */
    public static String anonimizar(String apodo) {
        if (apodo == null || apodo.isBlank()) {
            return "Un jugador";
        }
        String limpio = apodo.strip();
        if (limpio.length() <= 2) {
            return limpio.charAt(0) + "***";
        }
        return limpio.charAt(0) + "***" + limpio.charAt(limpio.length() - 1);
    }

    /** El nombre del producto, o una forma neutra si la subasta no lo guardo. */
    public static String producto(String nombre) {
        return nombre == null || nombre.isBlank() ? "el producto" : nombre;
    }
}
