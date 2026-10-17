package nexus.combate.reglas;

/**
 * Los efectos que dejan las acciones, las epicas y los objetos — §6.1.2,
 * Tablas 7, 8 a 19 y 20.
 *
 * <p>Cada tipo pertenece a una FAMILIA, y la familia decide cuando se descuenta
 * (decision D-B7-04; el documento solo dice «por dos turnos» o «en el siguiente
 * turno», no en que momento exacto):
 * <ul>
 *   <li>{@link Familia#PROPIO} — modifica lo que hace el PORTADOR: se aplica en
 *       sus acciones y se descuenta al terminar cada una. «+2 al dano por dos
 *       turnos» es el golpe de ahora y el del siguiente turno propio.</li>
 *   <li>{@link Familia#PROTECCION} — protege al portador de lo que le hagan
 *       los demas: dura hasta que EMPIEZA su siguiente turno. Una defensa que
 *       solo valiera en el turno propio no protegeria de nada: nadie te ataca
 *       mientras juegas tu.</li>
 *   <li>{@link Familia#POR_TURNO} — actua al empezar el turno del portador
 *       (sangrados y sanaciones que duran), y se descuenta al actuar.</li>
 *   <li>{@link Familia#VINCULO} — espera a que el portador caiga.</li>
 * </ul>
 */
public enum TipoDeEfecto {

    /** Suma a las tiradas de ataque del portador. */
    BONO_ATAQUE(Familia.PROPIO),
    /** Suma al dano de sus golpes. */
    BONO_DANO(Familia.PROPIO),
    /** Suma a sus sanaciones. */
    BONO_SANACION(Familia.PROPIO),
    /** Puntos de porcentaje de critico en su proximo ataque (Tabla 23). */
    BONO_CRITICO(Familia.PROPIO),
    /** Resta a sus tiradas de ataque (Cono de hielo sobre el enemigo). */
    PENALIZA_ATAQUE(Familia.PROPIO),
    /** Resta al dano que causa (Bola de hielo sobre el enemigo). */
    PENALIZA_DANO(Familia.PROPIO),

    /** Suma a su defensa (Mano de piedra). */
    BONO_DEFENSA(Familia.PROTECCION),
    /** No recibe dano fisico (Defensa feroz). */
    INMUNE_FISICO(Familia.PROTECCION),
    /** Resta al dano magico que recibe (Defensa feroz). */
    REDUCE_MAGICO(Familia.PROTECCION),
    /** No recibe ningun dano (Frio concentrado). */
    INMUNE_TOTAL(Familia.PROTECCION),
    /** Recibe la mitad y retorna el resto (Toma y lleva). */
    REFLEJA_MITAD(Familia.PROTECCION),
    /**
     * Quien lo golpea queda envenenado (Velo de Sombras, 7.8.14): {@code valor} de
     * dano por turno durante {@code turnos} turnos. Es una proteccion, asi que
     * acaba cuando empieza el siguiente turno del portador; los {@code turnos}
     * son los del veneno que deja, no los de esta proteccion.
     */
    ENVENENA_AL_ATACANTE(Familia.PROTECCION),

    /** Pierde vida al empezar su turno (Cierra sangrienta, Daga purulenta...). */
    DANO_POR_TURNO(Familia.POR_TURNO),
    /** Recupera vida al empezar su turno (Canto del Bosque, Yerbabuena, Benditas). */
    SANACION_POR_TURNO(Familia.POR_TURNO),

    /** Si cae, se reanima con el 20 % de su vida (Reanimador 3000). */
    VINCULO_REANIMACION(Familia.VINCULO);

    /** Cuando se descuenta un efecto. */
    public enum Familia { PROPIO, PROTECCION, POR_TURNO, VINCULO }

    private final Familia familia;

    TipoDeEfecto(Familia familia) {
        this.familia = familia;
    }

    public Familia familia() {
        return familia;
    }
}
