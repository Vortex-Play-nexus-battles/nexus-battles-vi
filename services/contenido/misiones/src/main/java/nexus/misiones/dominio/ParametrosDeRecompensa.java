package nexus.misiones.dominio;

import java.util.EnumMap;
import java.util.Map;

/**
 * Lo que el documento deja sin cifra y decide el PO. Viene de variables de
 * entorno (regla 10) con valores provisionales documentados en el README.
 *
 * @param experienciaPorCompletar «La finalizacion de una mision otorga puntos
 *                                de experiencia segun su dificultad» (6.1.1),
 *                                sin cifras: 0 mientras el PO no las fije
 * @param multiplicadorDeCreditos por escalon (7.8.11: «mejores recompensas»,
 *                                «premium»); provisional: el mismo factor que
 *                                las estadisticas (1, 1,5, 2)
 * @param epicaExigeCompletar     HU-MIS-007 dice «si lo derrota y completa la
 *                                mision»; el documento (7.8.4) solo «al ser
 *                                derrotados». Por omision manda el documento
 */
public record ParametrosDeRecompensa(
        Map<Dificultad, Double> experienciaPorCompletar,
        Map<Escalon, Double> multiplicadorDeCreditos,
        boolean epicaExigeCompletar) {

    public ParametrosDeRecompensa {
        experienciaPorCompletar = experienciaPorCompletar == null ? Map.of() : Map.copyOf(experienciaPorCompletar);
        multiplicadorDeCreditos = multiplicadorDeCreditos == null ? Map.of() : Map.copyOf(multiplicadorDeCreditos);
    }

    /** Los valores provisionales: sin experiencia por completar y el factor del documento. */
    public static ParametrosDeRecompensa provisionales() {
        return new ParametrosDeRecompensa(Map.of(), Map.of(), false);
    }

    public double experienciaPorCompletar(Dificultad dificultad) {
        return experienciaPorCompletar.getOrDefault(dificultad, 0.0);
    }

    public double multiplicadorDeCreditos(Escalon escalon) {
        Double fijado = multiplicadorDeCreditos.get(escalon);
        if (fijado != null) {
            return fijado;
        }
        Double delDocumento = escalon.multiplicadorDelDocumento();
        return delDocumento == null ? 1.0 : delDocumento;
    }

    /** Copia mutable para quien arma los parametros desde la configuracion. */
    public static Map<Escalon, Double> sinMultiplicadores() {
        return new EnumMap<>(Escalon.class);
    }
}
