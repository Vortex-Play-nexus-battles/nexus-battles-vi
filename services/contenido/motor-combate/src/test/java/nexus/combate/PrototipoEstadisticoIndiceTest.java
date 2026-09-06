package nexus.combate;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.EnumMap;
import java.util.Map;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PrototipoEstadisticoIndiceTest {

    private static final int EJECUCIONES = 80_000;
    private static final double TOLERANCIA_PUNTOS_PORCENTUALES = 1.5;

    @Test
    void frecuenciasObservadasCoincidenConTabla21ParaGuerreroArmas() {
        DistribucionEfectos distribucion = DistribucionEfectos.GUERRERO_ARMAS;
        RandomGenerator generador = new SecureRandom();

        Map<CategoriaEfecto, Integer> conteos = new EnumMap<>(CategoriaEfecto.class);
        for (CategoriaEfecto categoria : CategoriaEfecto.values()) {
            conteos.put(categoria, 0);
        }

        for (int i = 0; i < EJECUCIONES; i++) {
            int indice = GeneradorIndiceTabla.generarIndice(generador);
            CategoriaEfecto categoria = SelectorEfecto.seleccionar(indice, distribucion);
            conteos.merge(categoria, 1, Integer::sum);
        }

        verificarPorcentaje(conteos, CategoriaEfecto.CAUSAR_DANO, distribucion.causarDano());
        verificarPorcentaje(conteos, CategoriaEfecto.CAUSAR_DANO_CRITICO, distribucion.causarDanoCritico());
        verificarPorcentaje(conteos, CategoriaEfecto.EVADIR_EL_GOLPE, distribucion.evadirElGolpe());
        verificarPorcentaje(conteos, CategoriaEfecto.RESISTIR_EL_GOLPE, distribucion.resistirElGolpe());
        verificarPorcentaje(conteos, CategoriaEfecto.ESCAPAR_AL_GOLPE, distribucion.escaparAlGolpe());
        verificarPorcentaje(conteos, CategoriaEfecto.SIN_EFECTO, distribucion.sinEfecto());
    }

    private void verificarPorcentaje(Map<CategoriaEfecto, Integer> conteos, CategoriaEfecto categoria, int porcentajeEsperado) {
        double porcentajeObservado = 100.0 * conteos.get(categoria) / EJECUCIONES;
        double desviacion = Math.abs(porcentajeObservado - porcentajeEsperado);

        assertTrue(desviacion <= TOLERANCIA_PUNTOS_PORCENTUALES,
            categoria + ": esperado " + porcentajeEsperado + "%, observado "
                + String.format("%.2f", porcentajeObservado) + "%, desviacion "
                + String.format("%.2f", desviacion) + " puntos porcentuales");
    }
}
