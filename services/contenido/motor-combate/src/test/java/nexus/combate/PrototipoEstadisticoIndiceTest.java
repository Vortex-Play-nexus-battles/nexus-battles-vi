package nexus.combate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tablas 21 a 23 de punta a punta: indice normal -> fila -> efecto.
 *
 * <p>Hasta B7 esta prueba comprobaba que la frecuencia de cada efecto era su
 * porcentaje de filas, y pasaba porque el indice era uniforme. Con el indice
 * normal que pide §6.1.4 la frecuencia de cada efecto es la masa de la normal
 * sobre SU rango de filas (el orden de las filas es el de la Tabla 22), y eso
 * es lo que se comprueba ahora, para los seis prototipos y para la Tabla 23.
 * Semilla fija y miles de tiradas: tolerancia de 0,6 puntos (unas cinco
 * desviaciones del estimador con 100.000 tiradas).
 */
class PrototipoEstadisticoIndiceTest {

    private static final int EJECUCIONES = 100_000;
    private static final double TOLERANCIA = 0.006;

    static Stream<DistribucionEfectos> repartos() {
        return Stream.of(
                DistribucionEfectos.GUERRERO_TANQUE,
                DistribucionEfectos.GUERRERO_ARMAS,
                DistribucionEfectos.MAGO_FUEGO,
                DistribucionEfectos.MAGO_HIELO,
                DistribucionEfectos.PICARO_VENENO,
                DistribucionEfectos.PICARO_MACHETE,
                // Tabla 23: Guerrero Armas con +6 % de critico, a costa de «no causar dano».
                DistribucionEfectos.GUERRERO_ARMAS.aplicarEquipamiento(
                        List.of(new EfectoEquipamiento(CategoriaEfecto.CAUSAR_DANO_CRITICO, 6))));
    }

    @ParameterizedTest
    @MethodSource("repartos")
    @DisplayName("la frecuencia de cada efecto es la masa normal de sus filas")
    void frecuenciasSiguenLaNormalSobreLasFilas(DistribucionEfectos distribucion) {
        RandomGenerator generador = new Random(20260925L);
        Map<CategoriaEfecto, Integer> conteos = new EnumMap<>(CategoriaEfecto.class);
        for (int i = 0; i < EJECUCIONES; i++) {
            int indice = GeneradorIndiceTabla.generarIndice(generador);
            conteos.merge(SelectorEfecto.seleccionar(indice, distribucion), 1, Integer::sum);
        }

        TablaEfectos tabla = TablaEfectos.desde(distribucion);
        IndiceNormal indice = IndiceNormal.porOmision();
        int desde = 1;
        for (CategoriaEfecto categoria : tabla.orden()) {
            int filas = tabla.filasDe(categoria);
            double esperada = indice.probabilidadDeFilas(desde, desde + filas - 1);
            double observada = conteos.getOrDefault(categoria, 0) / (double) EJECUCIONES;
            assertEquals(esperada, observada, TOLERANCIA,
                    categoria + " (filas " + desde + "-" + (desde + filas - 1) + ")");
            desde += filas;
        }
    }

    @ParameterizedTest
    @MethodSource("repartos")
    @DisplayName("un efecto sin filas en la tabla no sale nunca")
    void efectoSinFilasNoSale(DistribucionEfectos distribucion) {
        RandomGenerator generador = new Random(7L);
        TablaEfectos tabla = TablaEfectos.desde(distribucion);
        for (int i = 0; i < 20_000; i++) {
            CategoriaEfecto sale = SelectorEfecto.seleccionar(
                    GeneradorIndiceTabla.generarIndice(generador), distribucion);
            assertTrue(tabla.filasDe(sale) > 0, sale + " no tiene filas y salio");
        }
    }
}
