package nexus.misiones.dominio.simulacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import nexus.misiones.dominio.Epica;
import nexus.misiones.dominio.HeroeEnMision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El simulador reproduce la mecanica del documento sin reimplementarla: quien
 * decide la jugada es el decisor (heroes, 7.8.5) y quien resuelve el golpe es
 * el motor (motor de combate). Aqui se prueba lo que SI es suyo: el orden de
 * los encuentros, la vida que se arrastra de uno a otro, el poder que se
 * recupera, quien cae, que se cuenta para el reporte y la experiencia.
 */
class SimuladorDeMisionTest {

    private static final HeroeEnMision HEROE =
            new HeroeEnMision("h-1", "Vorn", "Guerrero Armas", "p-1", 1, 0, 8, 44, 11);

    private static Rival regular(String nombre, int vida) {
        return new Rival(nombre, TipoDeRival.REGULAR, "Guerrero Tanque", 1, vida, 11, 10, List.of(), null);
    }

    private static Rival jefe(int vida) {
        return new Rival("El Guardian Eterno", TipoDeRival.JEFE, "Guerrero Tanque", 1, vida, 11, 10, List.of(), null);
    }

    private static final Epica VELO = new Epica("Velo de Sombras", "+2 a la defensa", "Intangible", null);

    @Test
    @DisplayName("un heroe que gana cada duelo completa la mision y derrota al jefe")
    void heroeQueGanaTodo() {
        GolpesPorAtacante golpes = new GolpesPorAtacante(Map.of("Guerrero Armas", 10, "Guerrero Tanque", 1));
        SimuladorDeMision simulador = new SimuladorDeMision(new DecisorBasico(), golpes, dado -> 10 * Math.pow(1.2, dado));

        ResultadoDeMision resultado = simulador.simular(HEROE, List.of(),
                List.of(regular("Sombras Corrompidas", 5), regular("Sombras Corrompidas", 5), jefe(20)),
                new AzarGuionado(true, 3, true, 4, true, 8), null);

        assertThat(resultado.exito()).isTrue();
        assertThat(resultado.jefeDerrotado()).isTrue();
        assertThat(resultado.encuentrosRegularesCompletos()).isTrue();
        assertThat(resultado.encuentrosCompletados()).isEqualTo(3);
        assertThat(resultado.enemigosDerrotados())
                .containsExactly(new ResultadoDeMision.EnemigoDerrotado("Sombras Corrompidas", 2));
        // Seccion 6.1.1: 10 x 1,2^(1d8) por cada enemigo no jugador, con el
        // dado que tiro el servidor (3, 4 y 8 en este guion).
        assertThat(resultado.dados()).containsExactly(3, 4, 8);
        assertThat(resultado.experiencia())
                .isCloseTo(10 * Math.pow(1.2, 3) + 10 * Math.pow(1.2, 4) + 10 * Math.pow(1.2, 8),
                        org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    @DisplayName("la vida no se recupera entre encuentros: el heroe cae en el segundo")
    void laVidaSeArrastra() {
        // Empieza el rival en los dos duelos y pega 30: el heroe (44) aguanta el
        // primero y cae en el segundo.
        GolpesPorAtacante golpes = new GolpesPorAtacante(Map.of("Guerrero Armas", 10, "Guerrero Tanque", 30));
        SimuladorDeMision simulador = new SimuladorDeMision(new DecisorBasico(), golpes, dado -> 1);

        ResultadoDeMision resultado = simulador.simular(HEROE, List.of(),
                List.of(regular("Sombras Corrompidas", 10), regular("Guardianes de Piedra", 10), jefe(10)),
                new AzarGuionado(false, 2, false), null);

        assertThat(resultado.exito()).isFalse();
        assertThat(resultado.jefeDerrotado()).isFalse();
        assertThat(resultado.encuentrosCompletados()).isEqualTo(1);
        assertThat(resultado.danoRecibido()).isEqualTo(60);
        assertThat(resultado.vidaMinimaPorcentaje()).isZero();
        assertThat(resultado.experiencia()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("el poder se gasta por la decision, sube 2 por turno y vuelve al maximo al empezar cada combate")
    void poderDelHeroe() {
        DecisorQueRecuerda decisor = new DecisorQueRecuerda("Embate sangriento", 4);
        GolpesPorAtacante golpes = new GolpesPorAtacante(Map.of("Guerrero Armas", 3, "Guerrero Tanque", 0));
        SimuladorDeMision simulador = new SimuladorDeMision(decisor, golpes, dado -> 1);

        simulador.simular(HEROE, List.of(List.of("Embate sangriento")),
                List.of(regular("A", 6), regular("B", 3)), new AzarGuionado(true, 1, true, 1), null);

        // Combate 1: 8 -> gasta 4 (4) -> +2 (6) -> gasta 4 (2) -> fin.
        // Combate 2: vuelve al maximo, 8.
        assertThat(decisor.poderes).containsExactly(8, 6, 8);
        assertThat(decisor.turnos).containsExactly(1, 2, 1);
    }

    @Test
    @DisplayName("el reporte cuenta criticos, turnos y las habilidades mas usadas")
    void estadisticasDelReporte() {
        GolpesPorAtacante golpes = new GolpesPorAtacante(Map.of("Guerrero Armas", 5, "Guerrero Tanque", 2));
        golpes.criticoPara("Guerrero Armas");
        SimuladorDeMision simulador = new SimuladorDeMision(new DecisorBasico(), golpes, dado -> 1);

        ResultadoDeMision resultado = simulador.simular(HEROE, List.of(),
                List.of(regular("A", 10)), new AzarGuionado(true, 1), null);

        assertThat(resultado.turnos()).isEqualTo(2);
        assertThat(resultado.criticos()).isEqualTo(2);
        assertThat(resultado.danoInfligido()).isEqualTo(10);
        assertThat(resultado.danoRecibido()).isEqualTo(2);
        assertThat(resultado.habilidadesMasUsadas())
                .containsExactly(new ResultadoDeMision.UsoDeHabilidad("Ataque básico", 2));
    }

    @Test
    @DisplayName("un duelo que nadie puede ganar se corta y la mision falla, en vez de girar para siempre")
    void dueloSinSalida() {
        GolpesPorAtacante golpes = new GolpesPorAtacante(Map.of("Guerrero Armas", 0, "Guerrero Tanque", 0));
        SimuladorDeMision simulador = new SimuladorDeMision(new DecisorBasico(), golpes, dado -> 1);

        ResultadoDeMision resultado = simulador.simular(HEROE, List.of(),
                List.of(regular("Inmortal", 10)), new AzarGuionado(true), null);

        assertThat(resultado.exito()).isFalse();
        assertThat(resultado.turnos()).isEqualTo(SimuladorDeMision.RONDAS_MAXIMAS_POR_COMBATE);
    }

    @Test
    @DisplayName("un Master derrotado queda en el resultado con su epica")
    void masterDerrotado() {
        GolpesPorAtacante golpes = new GolpesPorAtacante(Map.of("Guerrero Armas", 50, "Pícaro Veneno", 1));
        SimuladorDeMision simulador = new SimuladorDeMision(new DecisorBasico(), golpes, dado -> 1);
        Rival master = new Rival("Sombra del Olvido", TipoDeRival.MASTER, "Pícaro Veneno", 3, 30, 8, 8, List.of(), VELO);

        ResultadoDeMision resultado = simulador.simular(HEROE, List.of(), List.of(master),
                new AzarGuionado(true, 5), null);

        assertThat(resultado.masters()).containsExactly(
                new ResultadoDeMision.MasterEnfrentado("Sombra del Olvido", VELO, true));
        assertThat(resultado.enemigosDerrotados()).isEmpty();
    }

    @Test
    @DisplayName("con semilla de pruebas cada golpe pide al motor una semilla distinta y reproducible")
    void semillaDeGolpes() {
        GolpesPorAtacante golpes = new GolpesPorAtacante(Map.of("Guerrero Armas", 5, "Guerrero Tanque", 1));
        SimuladorDeMision simulador = new SimuladorDeMision(new DecisorBasico(), golpes, dado -> 1);

        simulador.simular(HEROE, List.of(), List.of(regular("A", 10)), new AzarGuionado(true, 1), 100L);

        assertThat(golpes.semillas).containsExactly(100L, 101L, 102L);
    }

    @Test
    @DisplayName("un rival con estrategia predefinida tambien la consulta al decisor")
    void rivalConEstrategia() {
        DecisorQueRecuerda decisor = new DecisorQueRecuerda("Golpe con escudo", 2);
        GolpesPorAtacante golpes = new GolpesPorAtacante(Map.of("Guerrero Armas", 50, "Guerrero Tanque", 1));
        SimuladorDeMision simulador = new SimuladorDeMision(decisor, golpes, dado -> 1);
        Rival conRotacion = new Rival("El Guardian Eterno", TipoDeRival.JEFE, "Guerrero Tanque", 1, 60, 11, 10,
                List.of(List.of("Golpe con escudo")), null);

        simulador.simular(HEROE, List.of(), List.of(conRotacion), new AzarGuionado(false, 1), null);

        assertThat(decisor.prototipos).contains("Guerrero Tanque");
    }

    // ------------------------------------------------------------- dobles

    /** El decisor sin estrategia: siempre ataque basico, sin gastar poder. */
    static final class DecisorBasico implements DecisorDeTurno {
        @Override
        public DecisionDeTurno decidir(TurnoParaDecidir turno) {
            return new DecisionDeTurno("Ataque básico", 0, turno.cursores());
        }
    }

    /** Usa una habilidad cuando le alcanza el poder y recuerda con que le llamaron. */
    static final class DecisorQueRecuerda implements DecisorDeTurno {
        final String habilidad;
        final int costo;
        final List<Integer> poderes = new ArrayList<>();
        final List<Integer> turnos = new ArrayList<>();
        final List<String> prototipos = new ArrayList<>();

        DecisorQueRecuerda(String habilidad, int costo) {
            this.habilidad = habilidad;
            this.costo = costo;
        }

        @Override
        public DecisionDeTurno decidir(TurnoParaDecidir turno) {
            prototipos.add(turno.prototipo());
            if (turno.prototipo().equals("Guerrero Armas")) {
                poderes.add(turno.poder());
                turnos.add(turno.turno());
            }
            if (turno.poder() >= costo) {
                return new DecisionDeTurno(habilidad, costo, List.of(1));
            }
            return new DecisionDeTurno("Ataque básico", 0, turno.cursores());
        }
    }

    /** Dano fijo por prototipo atacante; guarda las semillas que recibe. */
    static final class GolpesPorAtacante implements ResolutorDeGolpes {
        final Map<String, Integer> danoPorAtacante;
        final List<Long> semillas = new ArrayList<>();
        String conCritico;

        GolpesPorAtacante(Map<String, Integer> danoPorAtacante) {
            this.danoPorAtacante = danoPorAtacante;
        }

        void criticoPara(String atacante) {
            conCritico = atacante;
        }

        @Override
        public Golpe resolver(String prototipoAtacante, int defensaObjetivo, Long semilla) {
            if (semilla != null) {
                semillas.add(semilla);
            }
            return new Golpe(danoPorAtacante.getOrDefault(prototipoAtacante, 0),
                    prototipoAtacante.equals(conCritico));
        }
    }

    /**
     * Azar con guion: los booleanos dicen quien empieza cada duelo (true = el
     * heroe) y los enteros son los dados de experiencia, en el orden en que
     * se piden.
     */
    static final class AzarGuionado implements Azar {
        private final Deque<Object> guion = new ArrayDeque<>();

        AzarGuionado(Object... pasos) {
            guion.addAll(List.of(pasos));
        }

        @Override
        public int entre(int minimo, int maximo) {
            Object siguiente = guion.isEmpty() ? minimo : guion.poll();
            int valor = (Integer) siguiente;
            assertThat(valor).isBetween(minimo, maximo);
            return valor;
        }

        @Override
        public boolean acierta(double probabilidad) {
            Object siguiente = guion.isEmpty() ? Boolean.TRUE : guion.poll();
            return (Boolean) siguiente;
        }

        @Override
        public long largo() {
            return 7L;
        }
    }
}
