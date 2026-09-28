package nexus.combate;

import java.util.random.RandomGenerator;

/**
 * Indice de la tabla de efectos con la configuracion por omision.
 *
 * <p>Se conserva como fachada estatica para quien ya la usaba (la resolucion de
 * {@code /combate/ataques} y sus pruebas). Desde B7 delega en
 * {@link IndiceNormal}: el indice sigue una distribucion normal de verdad
 * (§6.1.4). Antes pasaba la gaussiana por su funcion de distribucion y salia
 * uniforme.
 */
public final class GeneradorIndiceTabla {

    private GeneradorIndiceTabla() {
    }

    public static int generarIndice(RandomGenerator generador) {
        return IndiceNormal.porOmision().generar(generador);
    }
}
