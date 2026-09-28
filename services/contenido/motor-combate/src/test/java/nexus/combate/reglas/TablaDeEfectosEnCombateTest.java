package nexus.combate.reglas;

import nexus.combate.CategoriaEfecto;
import nexus.combate.DistribucionEfectos;
import nexus.combate.IndiceNormal;
import nexus.combate.TablaEfectos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas estadisticas del combate de punta a punta, con miles de golpes de
 * verdad por el motor y semilla fija: las categorias salen con la masa de la
 * normal sobre sus filas (§6.1.4 y Tabla 22), el critico cae entre 120 y 180 %
 * con media ~150 %, y el critico del equipo (Tabla 23) se nota.
 */
class TablaDeEfectosEnCombateTest {

    private static final int GOLPES = 20_000;
    private final MotorDeAcciones motor = new MotorDeAcciones(new CatalogoDePrueba(), IndiceNormal.porOmision());

    /** Un saco de boxeo que no cae y no esquiva: defensa 0 y vida de sobra. */
    private static Contendiente saco() {
        Estadisticas estadisticas = new Estadisticas(10, 1_000_000, 0, new Formula(10, 1, 6), new Formula(0, 1, 4), null);
        return new Contendiente("saco", null, "Guerrero Tanque", 1, estadisticas, 1_000_000, 10, 0, Map.of(),
                List.of(), List.of(), List.of(), null);
    }

    private static Contendiente armas(String... equipo) {
        return new Contendiente("armas", null, "Guerrero Armas", 1, null, 44, 8, 0, Map.of(), List.of(),
                List.of(equipo), List.of(), null);
    }

    private Map<CategoriaEfecto, Integer> golpear(Contendiente atacante, long semilla, List<Integer> criticos) {
        Random azar = new Random(semilla);
        Map<CategoriaEfecto, Integer> conteos = new EnumMap<>(CategoriaEfecto.class);
        for (int i = 0; i < GOLPES; i++) {
            ResultadoDeAccion r = motor.resolver(new SolicitudDeAccion("ATAQUE_BASICO", "armas", null, false,
                    List.of(atacante, saco())), azar);
            conteos.merge(r.ataque().categoria(), 1, Integer::sum);
            if (r.ataque().categoria() == CategoriaEfecto.CAUSAR_DANO_CRITICO) {
                criticos.add(r.ataque().porcentajeDano());
            }
        }
        return conteos;
    }

    private static void comprobar(Map<CategoriaEfecto, Integer> conteos, DistribucionEfectos reparto) {
        TablaEfectos tabla = TablaEfectos.desde(reparto);
        IndiceNormal indice = IndiceNormal.porOmision();
        int desde = 1;
        for (CategoriaEfecto categoria : tabla.orden()) {
            int filas = tabla.filasDe(categoria);
            double esperada = indice.probabilidadDeFilas(desde, desde + filas - 1);
            double observada = conteos.getOrDefault(categoria, 0) / (double) GOLPES;
            assertEquals(esperada, observada, 0.012, categoria.name());
            desde += filas;
        }
    }

    @Test
    @DisplayName("las categorias del Guerrero Armas salen con la masa normal de sus filas")
    void categoriasDelGuerreroArmas() {
        comprobar(golpear(armas(), 11L, new java.util.ArrayList<>()), DistribucionEfectos.GUERRERO_ARMAS);
    }

    @Test
    @DisplayName("con la Espada de dos manos (+3 %) sale la Tabla 23 correspondiente")
    void categoriasConElCriticoDelArma() {
        comprobar(golpear(armas("Espada de dos manos"), 23L, new java.util.ArrayList<>()),
                DistribucionEfectos.GUERRERO_ARMAS.ajustarCritico(3));
    }

    @Test
    @DisplayName("el critico cae entre 120 y 180 % y su media ronda el 150 %")
    void porcentajeDelCritico() {
        List<Integer> criticos = new java.util.ArrayList<>();
        golpear(armas("Espada de dos manos"), 5L, criticos);
        assertTrue(criticos.size() > 1000, "hacen falta criticos para medir: " + criticos.size());
        double media = criticos.stream().mapToInt(Integer::intValue).average().orElseThrow();
        assertTrue(criticos.stream().allMatch(p -> p >= 120 && p <= 180));
        assertEquals(150.0, media, 1.5);
        assertTrue(criticos.contains(120) && criticos.contains(180), "los extremos tambien salen");
    }
}
