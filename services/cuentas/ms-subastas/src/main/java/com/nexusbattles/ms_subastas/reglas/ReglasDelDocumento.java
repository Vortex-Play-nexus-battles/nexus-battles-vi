package com.nexusbattles.ms_subastas.reglas;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

/**
 * Las cifras de 7.7 que fija el documento del curso y que, por el Project
 * Charter («valores numericos de ... limites y plazos son inalterables»), no
 * son parametros de ajuste: viven aqui como constantes, con la seccion de la
 * que salen al lado.
 *
 * <p>Las que el documento deja abiertas o declara configurables (el
 * incremento minimo, los topes de participacion, que pasa con lo que nadie
 * recoge) no estan aqui: salen de admin-parametros por {@link FuenteDeReglas}.
 */
public final class ReglasDelDocumento {

    /** 7.7.10: «Penalizacion del 50% de la comision pagada si se cancela». */
    public static final int PENALIZACION_CANCELACION_PORCENTAJE = 50;

    /** 7.7.10: la cancelacion «no [esta] permitida en las ultimas 6 horas de la subasta». */
    public static final Duration CANCELACION_PROHIBIDA_ULTIMAS = Duration.ofHours(6);

    /** 7.7.8: «Aviso 1 hora antes de finalizar subastas en las que se participa». */
    public static final Duration RECORDATORIO_ANTES_DEL_CIERRE = Duration.ofHours(1);

    /** 7.7.9: «Tiempo limite para reclamar (7 dias)». */
    public static final Duration PLAZO_PARA_RECOGER = Duration.ofDays(7);

    private ReglasDelDocumento() {
    }

    /**
     * La penalizacion de cancelar una subasta que costo {@code comision}: el
     * 50 %, a dos decimales (0,50 a 24 h; 1,50 a 48 h). Una comision
     * desconocida (subastas sembradas a mano antes de B8) no genera cobro: no
     * se cobra lo que no consta que se pago.
     */
    public static BigDecimal penalizacionDeCancelacion(BigDecimal comision) {
        if (comision == null || comision.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return comision.multiply(BigDecimal.valueOf(PENALIZACION_CANCELACION_PORCENTAJE))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
