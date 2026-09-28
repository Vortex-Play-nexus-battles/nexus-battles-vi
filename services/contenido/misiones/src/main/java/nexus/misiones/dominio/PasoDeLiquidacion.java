package nexus.misiones.dominio;

/**
 * Lo que queda por hacer fuera de este servicio cuando una ejecucion termina
 * (seccion 7.8.6, «finalizacion de la mision»), en el orden en que se hace.
 *
 * <p>Cada paso es idempotente del lado de quien lo recibe, asi que el trabajo
 * en segundo plano puede reintentarlo sin repetir nada (7.8.12, «sistema de
 * rollback en caso de errores»): liberar el heroe es idempotente por estado en
 * el inventario, los creditos por {@code refId} en ms-finanzas, las entregas
 * y los correos por {@code Idempotency-Key}.
 *
 * <p>Liberar va primero: el heroe vuelve al inventario aunque el libro de
 * creditos o el correo esten caidos.
 */
public enum PasoDeLiquidacion {
    /** El inventario libera al heroe y le suma la experiencia (inventario 1.6.0). */
    LIBERACION,
    /** Creditos al libro de ms-finanzas, {@code refId = mision-{ejecucion}}. */
    CREDITOS,
    /** Objetos del catalogo por {@code POST /inventario/entregas}, origen MISION. */
    BOTIN,
    /** La epica del Master, {@code Idempotency-Key = mision-{ejecucion}-epica}. */
    EPICA,
    /** Correo de finalizacion (7.8.9, «finalizacion de misiones en progreso»). */
    CORREO,
    /** Correo de epica (7.8.9, «obtencion de habilidades epicas de Master»). */
    CORREO_EPICA
}
