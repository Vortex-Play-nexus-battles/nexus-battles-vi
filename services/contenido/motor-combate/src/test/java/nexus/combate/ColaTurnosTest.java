package nexus.combate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de HU-JUE-001 - Sorteo del orden de turnos.
 *
 * Fuente: Proyecto Integrador II, seccion 6.1.3, p. 31. El orden se sortea
 * entre todos los participantes al iniciar y permanece invariable hasta que
 * termina el combate.
 */
class ColaTurnosTest {

    @Test
    @DisplayName("el sorteo incluye a cada participante exactamente una vez")
    void sorteoIncluyeATodosSinDuplicarlos() {
        List<String> participantes = List.of("ana", "bruno", "carla", "diego");

        ColaTurnos cola = ColaTurnos.sortear(participantes);

        assertEquals(participantes.size(), cola.secuencia().size());
        assertEquals(new HashSet<>(participantes), new HashSet<>(cola.secuencia()));
    }

    @Test
    @DisplayName("el orden sorteado no cambia al avanzar turnos ni rondas")
    void ordenPermaneceInvariableDuranteElCombate() {
        ColaTurnos cola = ColaTurnos.sortear(
                List.of("ana", "bruno", "carla"), new Random(29));
        List<String> ordenInicial = List.copyOf(cola.secuencia());

        for (int turno = 0; turno < 12; turno++) {
            cola.avanzar();
        }

        assertEquals(ordenInicial, cola.secuencia());
        assertEquals(ordenInicial.get(0), cola.participanteActivo());
        assertThrows(UnsupportedOperationException.class,
                () -> cola.secuencia().add("intruso"));
    }

    @Test
    @DisplayName("todos los participantes ocupan todas las posiciones con frecuencia equivalente")
    void sorteoEsEstadisticamenteUniforme() {
        List<String> participantes = List.of("ana", "bruno", "carla", "diego");
        // Parametro tecnico de prueba, no cifra de negocio de la historia.
        int ejecuciones = 24_000;
        int frecuenciaEsperada = ejecuciones / participantes.size();
        double tolerancia = frecuenciaEsperada * 0.05;
        int[][] frecuencias = new int[participantes.size()][participantes.size()];
        Random aleatorioReproducible = new Random(20260826L);

        for (int ejecucion = 0; ejecucion < ejecuciones; ejecucion++) {
            List<String> orden = ColaTurnos
                    .sortear(participantes, aleatorioReproducible)
                    .secuencia();
            for (int posicion = 0; posicion < orden.size(); posicion++) {
                int participante = participantes.indexOf(orden.get(posicion));
                frecuencias[participante][posicion]++;
            }
        }

        for (int participante = 0; participante < participantes.size(); participante++) {
            for (int posicion = 0; posicion < participantes.size(); posicion++) {
                double desviacion = Math.abs(
                        frecuencias[participante][posicion] - frecuenciaEsperada);
                assertNotEquals(0, frecuencias[participante][posicion]);
                assertTrue(
                        desviacion <= tolerancia,
                        "frecuencia fuera de tolerancia: "
                                + frecuencias[participante][posicion]);
            }
        }
    }

    @Test
    @DisplayName("el sorteo exige al menos dos participantes distintos y validos")
    void sorteoRechazaParticipantesInvalidos() {
        assertThrows(IllegalArgumentException.class,
                () -> ColaTurnos.sortear(List.of("solo"), new Random(1)));
        assertThrows(IllegalArgumentException.class,
                () -> ColaTurnos.sortear(List.of("ana", "ana"), new Random(1)));

        List<String> conIdentificadorVacio = new ArrayList<>();
        conIdentificadorVacio.add("ana");
        conIdentificadorVacio.add(" ");
        assertThrows(IllegalArgumentException.class,
                () -> ColaTurnos.sortear(conIdentificadorVacio, new Random(1)));
    }
}
