package com.nexusbattles.plataforma.metricasplataforma.moderacion;

/**
 * Por que un indicador accesorio del tablero de moderacion no se pudo leer
 * (RF-MET-001: «error en el calculo de un indicador, que se informa sin
 * bloquear el resto»).
 *
 * <p>El motivo tecnico completo sigue en {@code pendientes}, para quien
 * diagnostica; esta causa es lo que la consola necesita para decirlo en una
 * frase que entienda un administrador, sin leer una excepcion.
 */
public enum CausaDeIndisponibilidad {
    /** La fuente nego el permiso a quien mira el tablero (401/403). */
    SIN_PERMISO,
    /** No contesto, contesto tarde o con un error suyo (5xx). */
    NO_RESPONDE,
    /** Contesto algo que no es lo que dice su contrato, o le falta algo obligatorio. */
    RESPUESTA_INCOMPLETA,
    /** No admitio el periodo pedido (tiene su propio tope de dias). */
    RANGO_NO_ADMITIDO
}
