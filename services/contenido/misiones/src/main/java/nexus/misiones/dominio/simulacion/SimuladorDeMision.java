package nexus.misiones.dominio.simulacion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import nexus.misiones.dominio.HeroeEnMision;

/**
 * La simulacion automatica de una mision (seccion 7.8.6): la IA lleva al heroe
 * del jugador contra cada enemigo, uno detras de otro, hasta el jefe final.
 *
 * <h2>Que es de este simulador y que no</h2>
 *
 * <ul>
 *   <li>La jugada de cada turno la decide heroes ({@link DecisorDeTurno}):
 *       rotaciones con prioridad, poder, recarga y ataque basico de respaldo
 *       (7.8.5). Aqui no se reimplementa.</li>
 *   <li>El golpe lo resuelve el motor de combate ({@link ResolutorDeGolpes}):
 *       tirada, defensa y tabla de efectos (6.1.4). Aqui no hay un segundo
 *       motor.</li>
 *   <li>Lo que SI es de aqui: el orden de los encuentros, quien empieza cada
 *       duelo («el orden de los turnos para la primera ronda se determina
 *       aleatoriamente», 6.1.3), la vida que el heroe arrastra de un encuentro
 *       al siguiente, el poder que se gasta y se recupera («cada dos puntos por
 *       turno durante el combate» y «al concluir el combate», 6.1.1), la
 *       experiencia por enemigo derrotado con el 1d8 que tira el servidor, y lo
 *       que se cuenta para el reporte (7.8.8).</li>
 * </ul>
 *
 * <p>La vida NO se recupera entre encuentros: el documento no dice que lo haga,
 * y el objetivo «completar la mision sin que la vida del heroe baje del 50 %»
 * (7.8.14) solo tiene sentido si la vida se arrastra.
 *
 * <p>Las acciones de la Tabla 7 se deciden, gastan su poder y se cuentan en el
 * reporte, pero el efecto sobre el golpe es del motor: mientras su contrato
 * (motor-combate.yaml 1.1.0) no reciba la accion, resuelve el ataque basico.
 */
public class SimuladorDeMision {

    /**
     * Un duelo que nadie puede ganar (ninguno supera la defensa del otro) se
     * corta aqui y la mision se da por fallida: el heroe no pudo vencer. Sin
     * este tope, una ejecucion asi giraria para siempre dentro del trabajo en
     * segundo plano.
     */
    public static final int RONDAS_MAXIMAS_POR_COMBATE = 100;

    /** Seccion 6.1.1: el poder se recupera dos puntos por turno. */
    public static final int PODER_RECUPERADO_POR_TURNO = 2;

    private static final int HABILIDADES_EN_EL_REPORTE = 5;

    private final DecisorDeTurno decisor;
    private final ResolutorDeGolpes resolutor;
    private final TablaDeExperiencia experiencia;

    public SimuladorDeMision(DecisorDeTurno decisor, ResolutorDeGolpes resolutor, TablaDeExperiencia experiencia) {
        this.decisor = Objects.requireNonNull(decisor);
        this.resolutor = Objects.requireNonNull(resolutor);
        this.experiencia = Objects.requireNonNull(experiencia);
    }

    /**
     * @param estrategia      las rotaciones del heroe, ya validadas por heroes
     * @param rivales         en el orden en que se enfrentan (el jefe al final)
     * @param azar            la fuente de azar de esta ejecucion
     * @param semillaDeGolpes solo en pruebas reproducibles: cada golpe pide al
     *                        motor la semilla siguiente; nula en juego real
     */
    public ResultadoDeMision simular(HeroeEnMision heroe, List<List<String>> estrategia,
                                     List<Rival> rivales, Azar azar, Long semillaDeGolpes) {
        int regulares = (int) rivales.stream().filter(r -> r.tipo() == TipoDeRival.REGULAR).count();
        Registro registro = new Registro(heroe, semillaDeGolpes, regulares);
        int vidaDelHeroe = heroe.vida();

        for (Rival rival : rivales) {
            Duelo duelo = new Duelo(heroe, estrategia, rival, vidaDelHeroe, registro);
            duelo.jugar(azar.acierta(0.5));
            vidaDelHeroe = duelo.vidaDelHeroe;
            if (!duelo.rivalDerrotado()) {
                if (rival.tipo() == TipoDeRival.MASTER) {
                    registro.masterEnfrentado(rival, false);
                }
                // O el heroe cayo, o el duelo no tenia salida: la mision falla
                // aqui y los rivales que quedaban no se enfrentan.
                return registro.cerrar(false);
            }
            int dado = azar.entre(1, 8);
            registro.derrotado(rival, dado, experiencia.porEnemigoDerrotado(dado));
        }
        return registro.cerrar(true);
    }

    /** Un combate uno contra uno. El heroe llega con la vida que le quedaba. */
    private final class Duelo {

        private final HeroeEnMision heroe;
        private final List<List<String>> estrategia;
        private final Rival rival;
        private final Registro registro;

        private int vidaDelHeroe;
        private int vidaDelRival;
        // «El poder se recupera instantaneamente al concluir el combate»: cada
        // duelo empieza con el poder al maximo, sin recargas pendientes y con
        // las rotaciones en su primer paso.
        private int poderDelHeroe;
        private int poderDelRival;
        private final Map<String, Integer> usosDelHeroe = new HashMap<>();
        private final Map<String, Integer> usosDelRival = new HashMap<>();
        private List<Integer> cursoresDelHeroe = List.of();
        private List<Integer> cursoresDelRival = List.of();

        Duelo(HeroeEnMision heroe, List<List<String>> estrategia, Rival rival, int vidaDelHeroe, Registro registro) {
            this.heroe = heroe;
            this.estrategia = estrategia == null ? List.of() : estrategia;
            this.rival = rival;
            this.registro = registro;
            this.vidaDelHeroe = vidaDelHeroe;
            this.vidaDelRival = rival.vida();
            this.poderDelHeroe = heroe.poder();
            this.poderDelRival = rival.poder();
        }

        void jugar(boolean empiezaElHeroe) {
            for (int ronda = 1; ronda <= RONDAS_MAXIMAS_POR_COMBATE && ambosEnPie(); ronda++) {
                registro.turnos++;
                if (empiezaElHeroe) {
                    turnoDelHeroe(ronda);
                    turnoDelRival(ronda);
                } else {
                    turnoDelRival(ronda);
                    turnoDelHeroe(ronda);
                }
            }
        }

        boolean rivalDerrotado() {
            return vidaDelRival <= 0;
        }

        private boolean ambosEnPie() {
            return vidaDelHeroe > 0 && vidaDelRival > 0;
        }

        private void turnoDelHeroe(int ronda) {
            if (!ambosEnPie()) {
                return;
            }
            DecisionDeTurno decision = decisor.decidir(new TurnoParaDecidir(
                    heroe.prototipo(), heroe.nivel(), estrategia, ronda, poderDelHeroe, vidaDelHeroe,
                    usosDelHeroe, cursoresDelHeroe));
            poderDelHeroe = Math.max(0, poderDelHeroe - decision.costoDePoder());
            cursoresDelHeroe = decision.cursoresSiguientes();
            if (!decision.esAtaqueBasico()) {
                usosDelHeroe.put(decision.accion(), ronda);
            }
            registro.habilidadUsada(decision.accion());

            Golpe golpe = golpear(heroe.prototipo(), rival.defensa());
            vidaDelRival -= golpe.dano();
            registro.danoInfligido += golpe.dano();
            if (golpe.critico()) {
                registro.criticos++;
            }
            poderDelHeroe = Math.min(heroe.poder(), poderDelHeroe + PODER_RECUPERADO_POR_TURNO);
        }

        private void turnoDelRival(int ronda) {
            if (!ambosEnPie()) {
                return;
            }
            if (!rival.rotaciones().isEmpty()) {
                // «La IA controla a los enemigos con estrategias predefinidas»
                // (7.8.6). Sin estrategia, su jugada es el ataque basico y no
                // hace falta preguntarla.
                DecisionDeTurno decision = decisor.decidir(new TurnoParaDecidir(
                        rival.prototipo(), rival.nivel(), rival.rotaciones(), ronda, poderDelRival,
                        vidaDelRival, usosDelRival, cursoresDelRival));
                poderDelRival = Math.max(0, poderDelRival - decision.costoDePoder());
                cursoresDelRival = decision.cursoresSiguientes();
                if (!decision.esAtaqueBasico()) {
                    usosDelRival.put(decision.accion(), ronda);
                }
            }
            Golpe golpe = golpear(rival.prototipo(), heroe.defensa());
            vidaDelHeroe -= golpe.dano();
            registro.danoRecibido += golpe.dano();
            registro.vidaMinima = Math.min(registro.vidaMinima, Math.max(0, vidaDelHeroe));
            poderDelRival = Math.min(rival.poder(), poderDelRival + PODER_RECUPERADO_POR_TURNO);
        }

        private Golpe golpear(String atacante, int defensa) {
            try {
                return resolutor.resolver(atacante, defensa, registro.siguienteSemilla());
            } catch (SinCapacidadDeAtaque sanador) {
                return new Golpe(0, false);
            }
        }
    }

    /** Lo que se va contando para el reporte. */
    private static final class Registro {

        private final HeroeEnMision heroe;
        private final Long semillaDeGolpes;
        private final int regularesTotales;
        private long golpesDados;

        int danoInfligido;
        int danoRecibido;
        int turnos;
        int criticos;
        int vidaMinima;
        int encuentrosCompletados;
        boolean jefeDerrotado;
        double experiencia;
        final List<Integer> dados = new ArrayList<>();
        final Map<String, Integer> usos = new LinkedHashMap<>();
        final Map<String, Integer> derrotados = new LinkedHashMap<>();
        final List<ResultadoDeMision.MasterEnfrentado> masters = new ArrayList<>();
        int regularesDerrotados;

        Registro(HeroeEnMision heroe, Long semillaDeGolpes, int regularesTotales) {
            this.heroe = heroe;
            this.semillaDeGolpes = semillaDeGolpes;
            this.regularesTotales = regularesTotales;
            this.vidaMinima = heroe.vida();
        }

        Long siguienteSemilla() {
            if (semillaDeGolpes == null) {
                return null;
            }
            return semillaDeGolpes + golpesDados++;
        }

        void habilidadUsada(String accion) {
            usos.merge(accion, 1, Integer::sum);
        }

        void derrotado(Rival rival, int dado, double puntos) {
            encuentrosCompletados++;
            dados.add(dado);
            experiencia += puntos;
            switch (rival.tipo()) {
                case REGULAR -> {
                    regularesDerrotados++;
                    derrotados.merge(rival.nombre(), 1, Integer::sum);
                }
                case MASTER -> masterEnfrentado(rival, true);
                case JEFE -> jefeDerrotado = true;
            }
        }

        void masterEnfrentado(Rival master, boolean derrotado) {
            masters.add(new ResultadoDeMision.MasterEnfrentado(master.nombre(), master.epica(), derrotado));
        }

        /**
         * Cierra el registro. «Explorar las camaras» (7.8.14) se cumple si
         * cayeron todos los regulares, aunque despues el jefe o un Master
         * derrotara al heroe.
         */
        ResultadoDeMision cerrar(boolean exito) {
            boolean regularesCompletos = regularesDerrotados == regularesTotales;
            List<ResultadoDeMision.UsoDeHabilidad> habilidades = usos.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                    .limit(HABILIDADES_EN_EL_REPORTE)
                    .map(e -> new ResultadoDeMision.UsoDeHabilidad(e.getKey(), e.getValue()))
                    .toList();
            List<ResultadoDeMision.EnemigoDerrotado> enemigos = derrotados.entrySet().stream()
                    .map(e -> new ResultadoDeMision.EnemigoDerrotado(e.getKey(), e.getValue()))
                    .toList();
            int porcentaje = (int) Math.floor(100.0 * vidaMinima / heroe.vida());
            return new ResultadoDeMision(exito, jefeDerrotado, encuentrosCompletados, regularesCompletos,
                    danoInfligido, danoRecibido, turnos, criticos, habilidades, enemigos, masters,
                    porcentaje, experiencia, dados);
        }
    }
}
