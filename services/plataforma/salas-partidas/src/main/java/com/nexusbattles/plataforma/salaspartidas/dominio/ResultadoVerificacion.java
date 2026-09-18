package com.nexusbattles.plataforma.salaspartidas.dominio;

/**
 * Resultado de la verificacion previa de heroe — HU-SAL-003, RF-JUE-003.
 *
 * <p>Los cuatro valores son exactamente los del esquema
 * {@code ResultadoVerificacion} de {@code contracts/openapi/salas-partidas.yaml}.
 * Cada uno corresponde a una variante del dialogo que pinta
 * {@code validacion-heroe.js}: el nombre no es decorativo, es la clave por la
 * que la interfaz elige el texto y el aviso.
 *
 * <p><b>{@link #CREDITOS_INSUFICIENTES} no lo produce este servicio todavia.</b>
 * El puerto de creditos solo sabe <i>reservar</i> y <i>liberar</i>, nunca
 * consultar saldo, y esa ausencia es deliberada (ver {@code CreditosDelJugador}):
 * una consulta sin reserva deja hueco para gastar dos veces lo mismo. Mientras
 * no exista un proveedor de creditos con operacion de consulta, la verificacion
 * informa {@code creditosRequeridos} —que si conoce, porque es la recompensa de
 * la sala— y deja {@code creditosDisponibles} vacio. Inventar aqui un saldo
 * seria peor que no dar ninguno.
 */
public enum ResultadoVerificacion {

    /** El jugador tiene un heroe equipado y libre: puede entrar. */
    DISPONIBLE,

    /** No hay heroe, o el que hay no lleva nada equipado (RF-JUE-003). */
    SIN_HEROE_EQUIPADO,

    /** El heroe esta comprometido en otra actividad (RF-INV-009, RF-MIS-012). */
    HEROE_OCUPADO,

    /** El saldo no cubre la recompensa de la sala (RF-JUE-014). */
    CREDITOS_INSUFICIENTES
}
