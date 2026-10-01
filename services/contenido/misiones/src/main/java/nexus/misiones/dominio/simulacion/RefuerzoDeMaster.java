package nexus.misiones.dominio.simulacion;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * El piso que hace verdad «estadisticas superiores a enemigos regulares» (7.8.4, HU-SIM-006 criterio 1).
 *
 * <p>Un Master es el prototipo de su Master en el nivel del heroe mas dos (RG-107), con la vida y la defensa
 * escaladas por el escalon como cualquier enemigo. Eso casi siempre lo deja por encima, pero no siempre: el nivel
 * tope es 8 (el heroe de nivel 7 u 8 enfrenta un Master de nivel 8), un prototipo puede tener menos vida o defensa que
 * el de los regulares (un Pícaro Veneno contra un Guerrero Tanque), y los dados no escalan con el nivel (un 1d6 de dano
 * contra un 1d8). Aqui se comparan, uno por uno, la vida, la defensa, el ataque y el dano del Master con los del mas
 * fuerte de los regulares de SU mision y SU escalon (los regulares llegan ya escalados), y lo que quede por debajo
 * sube hasta quedar por encima.
 *
 * <p>El ataque y el dano son formulas con dados: se comparan por su valor esperado (la base mas la media de los
 * dados) y lo que falta se suma a la base; los dados no se tocan. Un Master sanador no tiene ataque ni dano, y un
 * regular sin formulas conocidas (el motor usa las del catalogo) no entra en esa comparacion; la vida y la defensa
 * se comparan siempre.
 *
 * <p><b>Decision provisional del PO.</b> El documento no da una cifra para «superiores». Se toma la menor que lo
 * hace verificable: {@value #MARGEN_MINIMO} punto por encima del regular mas fuerte, sin multiplicador
 * ({@link #FACTOR_SOBRE_EL_REGULAR_MAS_FUERTE} = 1,0). La superioridad real sale de los dos niveles de mas; este
 * piso solo la garantiza en los bordes. Si el PO quiere Master mas duros, por ejemplo con el 1,5 del escalon
 * Heroico (7.8.11), basta subir el factor.
 */
public final class RefuerzoDeMaster {

    /** Cuanto del regular mas fuerte hay que igualar antes de sumar el margen: 1,0 = igualarlo. */
    public static final double FACTOR_SOBRE_EL_REGULAR_MAS_FUERTE = 1.0;

    /** Lo menos que un Master le saca al regular mas fuerte, en puntos de cada estadistica. */
    public static final int MARGEN_MINIMO = 1;

    private RefuerzoDeMaster() {
    }

    /**
     * @param master   el Master ya preparado (estadisticas de su nivel, escaladas por el escalon)
     * @param regulares los enemigos regulares de la misma mision y el mismo escalon, tambien ya preparados
     * @return el Master, con lo que le faltara para quedar por encima del regular mas fuerte
     */
    public static Rival reforzar(Rival master, List<Rival> regulares) {
        Objects.requireNonNull(master);
        if (regulares.isEmpty()) {
            return master;
        }
        int vida = Math.max(master.vida(), pisoEntero(maximo(regulares, Rival::vida)));
        int defensa = Math.max(master.defensa(), pisoEntero(maximo(regulares, Rival::defensa)));
        Formula ataque = subirA(master.ataque(), regulares, Rival::ataque);
        Formula dano = subirA(master.dano(), regulares, Rival::dano);
        if (vida == master.vida() && defensa == master.defensa() && ataque == master.ataque()
                && dano == master.dano()) {
            return master;
        }
        return master.reforzado(vida, defensa, ataque, dano);
    }

    /** Si el Master esta por encima de cada regular en vida, defensa, ataque y dano (donde hay con que comparar). */
    public static boolean superaATodos(Rival master, List<Rival> regulares) {
        for (Rival regular : regulares) {
            if (master.vida() <= regular.vida() || master.defensa() <= regular.defensa()) {
                return false;
            }
            if (!supera(master.ataque(), regular.ataque()) || !supera(master.dano(), regular.dano())) {
                return false;
            }
        }
        return true;
    }

    private static boolean supera(Formula del, Formula contra) {
        return del == null || contra == null || del.esperado() > contra.esperado();
    }

    private static int maximo(List<Rival> regulares, Function<Rival, Integer> estadistica) {
        return regulares.stream().map(estadistica).mapToInt(Integer::intValue).max().orElse(0);
    }

    private static int pisoEntero(int delMasFuerte) {
        return (int) Math.floor(delMasFuerte * FACTOR_SOBRE_EL_REGULAR_MAS_FUERTE) + MARGEN_MINIMO;
    }

    /**
     * La formula del Master subida, si hace falta, hasta superar en valor esperado a la mas fuerte de los
     * regulares. Nula si el Master no la tiene o ningun regular la tiene; la misma instancia si ya basta.
     */
    private static Formula subirA(Formula delMaster, List<Rival> regulares, Function<Rival, Formula> formula) {
        if (delMaster == null) {
            return null;
        }
        double masFuerte = regulares.stream().map(formula).filter(Objects::nonNull).mapToDouble(Formula::esperado)
                .max().orElse(Double.NEGATIVE_INFINITY);
        if (masFuerte == Double.NEGATIVE_INFINITY) {
            return delMaster;
        }
        double piso = masFuerte * FACTOR_SOBRE_EL_REGULAR_MAS_FUERTE + MARGEN_MINIMO;
        if (delMaster.esperado() >= piso) {
            return delMaster;
        }
        int falta = (int) Math.ceil(piso - delMaster.esperado());
        return new Formula(delMaster.base() + falta, delMaster.cantidadDados(), delMaster.caras());
    }
}
