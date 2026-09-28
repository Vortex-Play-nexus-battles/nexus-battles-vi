package nexus.combate.reglas;

/**
 * Por que el servidor no deja jugar una accion. Espejo del esquema
 * {@code MotivoDeRechazo} de {@code motor-combate.yaml}.
 */
public enum MotivoDeRechazo {
    /** No es una accion de este heroe, ni basica, ni una epica que tenga. */
    ACCION_DESCONOCIDA,
    /** RC-01: todavia no la ha aprendido (niveles 1, 4 y 8). */
    BLOQUEADA_POR_NIVEL,
    /** §6.1.2: un turno de carga (dos las epicas). */
    EN_CARGA,
    /** Hay mas de un objetivo posible y no dijo cual. */
    OBJETIVO_REQUERIDO,
    /** El objetivo no vale para esta accion. */
    OBJETIVO_INVALIDO,
    /** Sin vida no se actua. */
    EJECUTOR_CAIDO,
    /** §6.1.1: a un sanador le esta vedado infligir dano. */
    SANADOR_NO_ATACA,
    /** No tiene esa epica. */
    EPICA_NO_DISPONIBLE,
    /** La epica no hace nada para su tipo de heroe (Tabla 20: «No aplica»). */
    EPICA_SIN_EFECTO,
    /** Su prototipo no tiene fila en la Tabla 21: no se inventa una. */
    SIN_TABLA_DE_EFECTOS
}
