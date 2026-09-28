package nexus.misiones.dominio.simulacion;

import java.util.ArrayList;
import java.util.List;
import nexus.misiones.dominio.Categoria;
import nexus.misiones.dominio.EpicaDeTabla20;
import nexus.misiones.dominio.MasterDeMision;
import nexus.misiones.dominio.Mision;

/**
 * Que Master aparecen en una ejecucion (seccion 7.8.4).
 *
 * <p>Candidatos: los Master propios de la mision (el ejemplo del documento
 * trae «Sombra del Olvido», 15 %) y el Master afin al tipo del heroe enviado,
 * con la «probabilidad de Master en una mision» de la Tabla 20 («cada tipo de
 * heroe tiene Master asociados con epicas especificas»). Cada candidato se
 * sortea por separado.
 *
 * <p>Una tirada por mision, como dice la Tabla 20; en exploracion, una por
 * cada 24 horas de duracion («pueden aparecer multiples Master en misiones
 * largas» y «mayor probabilidad de enfrentar enemigos Master», 7.8.2 y 7.8.4).
 * La regla de una tirada por dia es una decision tecnica provisional: el
 * documento dice «mayor» sin cuantificarla.
 */
public final class TiradaDeMasters {

    private static final double HORAS_POR_TIRADA_EN_EXPLORACION = 24;

    private TiradaDeMasters() {
    }

    public static int tiradas(Mision mision) {
        if (mision.categoria() != Categoria.EXPLORACION) {
            return 1;
        }
        return Math.max(1, (int) Math.floor(mision.duracionHoras() / HORAS_POR_TIRADA_EN_EXPLORACION));
    }

    /**
     * Probabilidad de que aparezca al menos uno de los Master PROPIOS de la
     * mision, con todas sus tiradas: 1 - producto de (1 - p) por candidato y
     * tirada. Es lo que ensena el detalle («probabilidad de aparicion», 7.8.4);
     * el Master afin al heroe depende del heroe que se envie y va aparte.
     *
     * @return nula si la mision no tiene Master propios
     */
    public static Double probabilidadDeAlguno(Mision mision) {
        if (mision.masters().isEmpty()) {
            return null;
        }
        double ninguno = 1.0;
        for (int tirada = 0; tirada < tiradas(mision); tirada++) {
            for (MasterDeMision master : mision.masters()) {
                ninguno *= 1.0 - master.probabilidad();
            }
        }
        return 1.0 - ninguno;
    }

    public static List<MasterDeMision> quienesAparecen(
            Mision mision, String prototipoDelHeroe, List<EpicaDeTabla20> tabla20, Azar azar) {
        List<MasterDeMision> candidatos = new ArrayList<>(mision.masters());
        tabla20.stream()
                .filter(fila -> fila.prototipo().equals(prototipoDelHeroe))
                .findFirst()
                .map(EpicaDeTabla20::comoMaster)
                .ifPresent(candidatos::add);

        List<MasterDeMision> aparecen = new ArrayList<>();
        for (int tirada = 0; tirada < tiradas(mision); tirada++) {
            for (MasterDeMision candidato : candidatos) {
                if (azar.acierta(candidato.probabilidad())) {
                    aparecen.add(candidato);
                }
            }
        }
        return List.copyOf(aparecen);
    }
}
