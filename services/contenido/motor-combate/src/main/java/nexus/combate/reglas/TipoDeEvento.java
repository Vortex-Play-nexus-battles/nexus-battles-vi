package nexus.combate.reglas;

/** Lo que puede pasar en una accion o al empezar un turno, para narrarlo. */
public enum TipoDeEvento {
    DANO,
    SANACION,
    EFECTO_APLICADO,
    EFECTO_TERMINADO,
    DANO_POR_TURNO,
    SANACION_POR_TURNO,
    REFLEJO,
    PROTEGIDO,
    REANIMACION,
    PODER_PERDIDO,
    PODER_RECUPERADO,
    CAIDO,
    VALOR_BASE
}
