package nexus.misiones.dominio;

/**
 * Lo que queda por hacer fuera de este servicio cuando una ejecucion termina
 * (seccion 7.8.6, «finalizacion de la mision»), en el orden en que se hace.
 *
 * <p>Cada paso es idempotente del lado de quien lo recibe, asi que el trabajo
 * en segundo plano puede reintentarlo sin repetir nada (7.8.12, «sistema de
 * rollback en caso de errores»): liberar el heroe es idempotente por estado en
 * el inventario, los creditos por {@code refId} en ms-finanzas, las entregas
 * y los correos por {@code Idempotency-Key}, y los avisos por su {@code id}
 * (notificaciones.yaml: el mismo id responde 409 y no se duplica).
 *
 * <p>Liberar va primero: el heroe vuelve al inventario aunque el libro de
 * creditos, el correo o la bandeja esten caidos. Los avisos van al final:
 * cuentan lo que ya se entrego.
 *
 * <p>El orden de la declaracion ES el orden de la liquidacion: un paso nuevo
 * va al final para no adelantar nada a lo que ya se hacia.
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
    CORREO_EPICA,
    /**
     * Aviso en la bandeja del jugador de la finalizacion, con el detalle de
     * cada recompensa (7.8.9; 7.8.10, «notificacion detallada de cada
     * recompensa recibida»; 7.8.12, «notificaciones en tiempo real al
     * completarse»; RF-NOT-004). {@code id = mision-{ejecucion}-aviso}.
     */
    AVISO,
    /** Aviso de la epica de Master obtenida (7.8.9; RF-NOT-004). {@code id = mision-{ejecucion}-aviso-epica}. */
    AVISO_EPICA,
    /**
     * Aviso de cada mision de historia que esta ejecucion desbloqueo al
     * completarse por primera vez (7.8.9, «nuevas misiones disponibles»; 7.8.2,
     * la historia «se desbloquea secuencialmente»; RF-NOT-004).
     * {@code id = mision-{ejecucion}-aviso-desbloqueo-{mision}}.
     */
    AVISO_DESBLOQUEO
}
