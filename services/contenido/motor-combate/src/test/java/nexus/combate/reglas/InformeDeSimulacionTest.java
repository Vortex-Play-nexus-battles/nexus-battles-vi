package nexus.combate.reglas;

import nexus.combate.reglas.SimuladorDeCombates.Estrategia;
import nexus.combate.reglas.SimuladorDeCombates.Metricas;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * El informe de la IA tactica (D-41): no corre en la integracion continua
 * (la prueba ligera es {@link SimulacionDeLaMaquinaTest}); se pide a mano para
 * medir con muchas partidas:
 *
 * <pre>
 * ./gradlew :services:contenido:motor-combate:test --tests '*InformeDeSimulacionTest' \
 *     -Dsimulacion.informe=true -Dsimulacion.partidas=100
 * </pre>
 *
 * Imprime en Markdown, por nivel, la matriz 8×8 de prototipos con la maquina
 * en NORMAL contra si misma, y en espejo las tres dificultades contra la regla
 * fija anterior (D-B7-12) y entre ellas. Falla si aparece una sola jugada
 * ilegal, un auto-dano o fuego amigo.
 */
@EnabledIfSystemProperty(named = "simulacion.informe", matches = "true")
class InformeDeSimulacionTest {

    private static final List<Integer> NIVELES = List.of(1, 4, 8);

    @Test
    void informe() {
        int partidas = Integer.getInteger("simulacion.partidas", 20);
        int tope = Integer.getInteger("simulacion.tope", 200);
        SimuladorDeCombates sim = new SimuladorDeCombates(tope);
        StringBuilder sb = new StringBuilder();
        Metricas total = new Metricas();

        sb.append("## Matriz 8×8 · NORMAL contra NORMAL · ").append(partidas).append(" partidas por casilla\n\n");
        for (int nivel : NIVELES) {
            sb.append("### Nivel ").append(nivel).append(" — % de victorias de la fila (tablas aparte)\n\n| A \\ B |");
            for (String b : SimuladorDeCombates.PROTOTIPOS) {
                sb.append(' ').append(corto(b)).append(" |");
            }
            sb.append("\n|---|").append("---|".repeat(SimuladorDeCombates.PROTOTIPOS.size())).append('\n');
            Metricas delNivel = new Metricas();
            for (String a : SimuladorDeCombates.PROTOTIPOS) {
                sb.append("| ").append(corto(a)).append(" |");
                for (String b : SimuladorDeCombates.PROTOTIPOS) {
                    Metricas casilla = new Metricas();
                    for (int i = 0; i < partidas; i++) {
                        casilla.sumar(sim.duelo(a, Estrategia.NORMAL, b, Estrategia.NORMAL, nivel,
                                semilla(nivel, a, b, i)));
                    }
                    delNivel.sumar(casilla);
                    sb.append(' ').append(pct(casilla.tasaA()));
                    if (casilla.tablas > 0) {
                        sb.append(" (").append(casilla.tablas).append("t)");
                    }
                    sb.append(" |");
                }
                sb.append('\n');
            }
            sb.append('\n').append(resumen("Nivel " + nivel, delNivel)).append("\n\n");
            total.sumar(delNivel);
        }

        sb.append("## Espejo (mismo prototipo y nivel) · ").append(partidas)
                .append(" partidas por prototipo y nivel\n\n")
                .append("| A contra B | Victorias A | Victorias B | Tablas | Turnos medios | Especiales/acción "
                        + "| Épicas/acción | Sanaciones/acción | Defensas/acción | Ilegales | Auto-daño | Fuego amigo |\n")
                .append("|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        Estrategia[][] cruces = {
                {Estrategia.FACIL, Estrategia.REGLA_FIJA},
                {Estrategia.NORMAL, Estrategia.REGLA_FIJA},
                {Estrategia.DIFICIL, Estrategia.REGLA_FIJA},
                {Estrategia.NORMAL, Estrategia.FACIL},
                {Estrategia.DIFICIL, Estrategia.FACIL},
                {Estrategia.DIFICIL, Estrategia.NORMAL},
        };
        for (Estrategia[] cruce : cruces) {
            Metricas m = new Metricas();
            for (int nivel : NIVELES) {
                for (String p : SimuladorDeCombates.PROTOTIPOS.subList(0, 6)) {
                    for (int i = 0; i < partidas; i++) {
                        m.sumar(sim.duelo(p, cruce[0], p, cruce[1], nivel, semilla(nivel, p, cruce[0].name(), i)));
                    }
                }
            }
            total.sumar(m);
            sb.append("| ").append(cruce[0]).append(" contra ").append(cruce[1]).append(" | ")
                    .append(pct(m.tasaA())).append(" | ").append(pct((double) m.victoriasB / m.partidas))
                    .append(" | ").append(m.tablas).append(" | ")
                    .append(String.format(Locale.ROOT, "%.1f", m.turnosMedios())).append(" | ")
                    .append(pct(m.porAccion(m.especiales))).append(" | ").append(pct(m.porAccion(m.epicas)))
                    .append(" | ").append(pct(m.porAccion(m.sanaciones))).append(" | ")
                    .append(pct(m.porAccion(m.defensas))).append(" | ").append(m.ilegales).append(" | ")
                    .append(m.autoDano).append(" | ").append(m.fuegoAmigo).append(" |\n");
        }

        // Los dos equipos son distintos: se juega cada partida dos veces, con
        // las dificultades cambiadas de equipo, para que la composicion no
        // decida por la dificultad.
        sb.append("\n## 3 contra 3 (cooperativo) · DIFICIL contra NORMAL, cada partida con los equipos cambiados\n\n");
        Metricas dificilComoA = new Metricas();
        Metricas dificilComoB = new Metricas();
        for (int nivel : NIVELES) {
            for (int i = 0; i < partidas; i++) {
                long s = semilla(nivel, "equipos", "", i);
                dificilComoA.sumar(sim.partida(equipoUno(nivel), Estrategia.DIFICIL, equipoDos(nivel),
                        Estrategia.NORMAL, true, s));
                dificilComoB.sumar(sim.partida(equipoUno(nivel), Estrategia.NORMAL, equipoDos(nivel),
                        Estrategia.DIFICIL, true, s));
            }
        }
        Metricas equipos = new Metricas();
        equipos.sumar(dificilComoA);
        equipos.sumar(dificilComoB);
        total.sumar(equipos);
        int ganaDificil = dificilComoA.victoriasA + dificilComoB.victoriasB;
        int ganaNormal = dificilComoA.victoriasB + dificilComoB.victoriasA;
        sb.append(String.format(Locale.ROOT, "DIFICIL gana %s, NORMAL %s, tablas %d.%n%n",
                pct((double) ganaDificil / equipos.partidas), pct((double) ganaNormal / equipos.partidas),
                equipos.tablas));
        sb.append(resumen("3 contra 3", equipos)).append("\n\n");
        sb.append(latencias()).append('\n');
        sb.append(resumen("TOTAL", total)).append('\n');

        System.out.println(sb);
        assertEquals(0, total.ilegales, "jugadas ilegales");
        assertEquals(0, total.autoDano, "auto-daño");
        assertEquals(0, total.fuegoAmigo, "fuego amigo");
    }

    private static List<Contendiente> equipoUno(int nivel) {
        return List.of(SimuladorDeCombates.heroe("A1", "Guerrero Tanque", nivel, 1),
                SimuladorDeCombates.heroe("A2", "Mago Fuego", nivel, 1),
                SimuladorDeCombates.heroe("A3", "Chamán", nivel, 1));
    }

    private static List<Contendiente> equipoDos(int nivel) {
        return List.of(SimuladorDeCombates.heroe("B1", "Guerrero Armas", nivel, 2),
                SimuladorDeCombates.heroe("B2", "Pícaro Veneno", nivel, 2),
                SimuladorDeCombates.heroe("B3", "Médico", nivel, 2));
    }

    /**
     * Cuanto tarda la maquina en decidir, en el peor caso de mesa (3 contra 3
     * en nivel 8, todas las acciones y la epica disponibles) y en un duelo.
     * Contra el objetivo de 500 ms de extremo a extremo (HU-REN-001).
     */
    private static String latencias() {
        StringBuilder sb = new StringBuilder("## Latencia de la decisión (en este equipo)\n\n"
                + "| Mesa | Dificultad | Media | Máximo |\n|---|---|---|---|\n");
        List<Contendiente> duelo = List.of(SimuladorDeCombates.heroe("ia", "Guerrero Armas", 8, null),
                SimuladorDeCombates.heroe("r", "Mago Hielo", 8, null));
        List<Contendiente> tres = List.of(SimuladorDeCombates.heroe("ia", "Mago Fuego", 8, 1),
                SimuladorDeCombates.heroe("a2", "Guerrero Tanque", 8, 1),
                SimuladorDeCombates.heroe("a3", "Chamán", 8, 1),
                SimuladorDeCombates.heroe("b1", "Guerrero Armas", 8, 2),
                SimuladorDeCombates.heroe("b2", "Pícaro Veneno", 8, 2),
                SimuladorDeCombates.heroe("b3", "Médico", 8, 2));
        for (DificultadDeLaMaquina d : DificultadDeLaMaquina.values()) {
            MotorDeAcciones motor = new MotorDeAcciones(new CatalogoDePrueba(), nexus.combate.IndiceNormal.porOmision(),
                    d);
            for (boolean enEquipos : List.of(false, true)) {
                List<Contendiente> mesa = enEquipos ? tres : duelo;
                long maximo = 0;
                long suma = 0;
                int veces = 60;
                for (int i = 0; i < veces + 20; i++) {
                    long t0 = System.nanoTime();
                    motor.resolver(new SolicitudDeAccion(Reglamento.DECISION_DE_LA_MAQUINA, "ia", null, enEquipos,
                            mesa), new java.util.Random(i));
                    long t = System.nanoTime() - t0;
                    if (i >= 20) {
                        suma += t;
                        maximo = Math.max(maximo, t);
                    }
                }
                sb.append(String.format(Locale.ROOT, "| %s | %s | %.1f ms | %.1f ms |%n",
                        enEquipos ? "3 contra 3, nivel 8" : "1 contra 1, nivel 8", d, suma / 1e6 / veces,
                        maximo / 1e6));
            }
        }
        return sb.toString();
    }

    private static String resumen(String titulo, Metricas m) {
        return String.format(Locale.ROOT,
                "**%s**: %d partidas · victorias A %s · B %s · tablas %d · %.1f turnos de media · %d acciones "
                        + "(especiales %s, épicas %s, sanaciones %s, defensas %s) · ilegales %d · auto-daño %d · "
                        + "fuego amigo %d · daño reflejado (contractual) %d",
                titulo, m.partidas, pct(m.tasaA()), pct((double) m.victoriasB / Math.max(1, m.partidas)), m.tablas,
                m.turnosMedios(), m.acciones, pct(m.porAccion(m.especiales)), pct(m.porAccion(m.epicas)),
                pct(m.porAccion(m.sanaciones)), pct(m.porAccion(m.defensas)), m.ilegales, m.autoDano,
                m.fuegoAmigo, m.danoReflejado);
    }

    private static long semilla(int nivel, String a, String b, int i) {
        return (31L * nivel + a.hashCode()) * 1_000_003L + b.hashCode() * 7919L + i;
    }

    private static String pct(double x) {
        return String.format(Locale.ROOT, "%.0f %%", 100 * x);
    }

    private static String corto(String prototipo) {
        return switch (prototipo) {
            case "Guerrero Tanque" -> "G.Tanque";
            case "Guerrero Armas" -> "G.Armas";
            case "Mago Fuego" -> "M.Fuego";
            case "Mago Hielo" -> "M.Hielo";
            case "Pícaro Veneno" -> "P.Veneno";
            case "Pícaro Machete" -> "P.Machete";
            default -> prototipo;
        };
    }
}
