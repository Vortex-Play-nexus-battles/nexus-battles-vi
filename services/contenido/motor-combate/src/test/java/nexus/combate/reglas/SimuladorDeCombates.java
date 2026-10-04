package nexus.combate.reglas;

import nexus.combate.IndiceNormal;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.random.RandomGenerator;

/**
 * Partidas completas contra el motor real, de principio a fin, para medir a la
 * maquina (D-41): quien gana, cuanto duran, que juega y —lo que no puede
 * pasar nunca— jugadas ilegales, dano a uno mismo y dano a un companero.
 *
 * <p>Cada partida sigue el §6.1.3: orden de turnos sorteado al empezar e
 * invariable; al empezar cada turno, {@link MotorDeAcciones#iniciarTurno}
 * (efectos por turno y +2 de poder); despues, una accion. Termina cuando queda
 * un solo bando en pie, o en tablas al llegar al tope de turnos.
 *
 * <p>Un bando juega con una {@link Estrategia}: la maquina en una de sus tres
 * dificultades, o la regla fija de D-B7-12 como referencia contra la que
 * comparar.
 */
final class SimuladorDeCombates {

    /** Los ocho prototipos de la Tabla 6. */
    static final List<String> PROTOTIPOS = List.of("Guerrero Tanque", "Guerrero Armas", "Mago Fuego", "Mago Hielo",
            "Pícaro Veneno", "Pícaro Machete", "Chamán", "Médico");

    /** La epica afin de cada prototipo (Tabla 20), para que la maquina tambien las juegue. */
    static final Map<String, String> EPICA_AFIN = Map.of(
            "Guerrero Tanque", "Golpe de defensa", "Guerrero Armas", "Segundo impulso",
            "Mago Fuego", "Luz cegadora", "Mago Hielo", "Frío concentrado",
            "Pícaro Veneno", "Toma y lleva", "Pícaro Machete", "Intimidación sangrienta",
            "Chamán", "Té changua", "Médico", "Reanimador 3000");

    /** Como juega un bando. */
    enum Estrategia {
        /** La regla fija anterior (D-B7-12): referencia. */
        REGLA_FIJA(null),
        FACIL(DificultadDeLaMaquina.FACIL),
        NORMAL(DificultadDeLaMaquina.NORMAL),
        DIFICIL(DificultadDeLaMaquina.DIFICIL);

        final DificultadDeLaMaquina dificultad;

        Estrategia(DificultadDeLaMaquina dificultad) {
            this.dificultad = dificultad;
        }
    }

    /** Lo que se mide de una o muchas partidas, por bando. */
    static final class Metricas {
        int partidas;
        int victoriasA;
        int victoriasB;
        int tablas;
        long turnos;
        long acciones;
        long especiales;
        long epicas;
        long sanaciones;
        long defensas;
        long ilegales;
        long autoDano;
        long fuegoAmigo;
        long danoReflejado;

        void sumar(Metricas otra) {
            partidas += otra.partidas;
            victoriasA += otra.victoriasA;
            victoriasB += otra.victoriasB;
            tablas += otra.tablas;
            turnos += otra.turnos;
            acciones += otra.acciones;
            especiales += otra.especiales;
            epicas += otra.epicas;
            sanaciones += otra.sanaciones;
            defensas += otra.defensas;
            ilegales += otra.ilegales;
            autoDano += otra.autoDano;
            fuegoAmigo += otra.fuegoAmigo;
            danoReflejado += otra.danoReflejado;
        }

        double tasaA() {
            return partidas == 0 ? 0 : (double) victoriasA / partidas;
        }

        double turnosMedios() {
            return partidas == 0 ? 0 : (double) turnos / partidas;
        }

        double porAccion(long cuantas) {
            return acciones == 0 ? 0 : (double) cuantas / acciones;
        }
    }

    private final CatalogoDePrueba catalogo = new CatalogoDePrueba();
    private final Map<DificultadDeLaMaquina, MotorDeAcciones> motores = new EnumMap<>(DificultadDeLaMaquina.class);
    private final MotorDeAcciones motorDeReferencia;
    private final int topeDeTurnos;

    SimuladorDeCombates(int topeDeTurnos) {
        IndiceNormal indice = IndiceNormal.porOmision();
        for (DificultadDeLaMaquina d : DificultadDeLaMaquina.values()) {
            motores.put(d, new MotorDeAcciones(catalogo, indice, d));
        }
        this.motorDeReferencia = motores.get(DificultadDeLaMaquina.NORMAL);
        this.topeDeTurnos = topeDeTurnos;
    }

    /** Un heroe de nivel {@code nivel}, a vida y poder llenos, con su epica afin. */
    static Contendiente heroe(String id, String prototipo, int nivel, Integer equipo) {
        return new Contendiente(id, equipo, prototipo, nivel, null, Integer.MAX_VALUE, Integer.MAX_VALUE, 0,
                Map.of(), List.of(), List.of(), List.of(EPICA_AFIN.get(prototipo)), null);
    }

    /** Uno contra uno: A contra B, al mismo nivel. */
    Metricas duelo(String prototipoA, Estrategia a, String prototipoB, Estrategia b, int nivel, long semilla) {
        return partida(List.of(heroe("A1", prototipoA, nivel, null)), a,
                List.of(heroe("B1", prototipoB, nivel, null)), b, false, semilla);
    }

    /**
     * Una partida completa. Sin equipos ({@code porEquipos} falso) cada bando
     * debe tener un solo heroe; con equipos, los del bando A llevan el equipo 1
     * y los del B el 2.
     */
    Metricas partida(List<Contendiente> bandoA, Estrategia a, List<Contendiente> bandoB, Estrategia b,
                     boolean porEquipos, long semilla) {
        Random azar = new Random(semilla);
        Map<String, Estrategia> estrategia = new LinkedHashMap<>();
        Map<String, Character> bando = new LinkedHashMap<>();
        List<Contendiente> combatientes = new ArrayList<>();
        for (Contendiente c : bandoA) {
            estrategia.put(c.id(), a);
            bando.put(c.id(), 'A');
            combatientes.add(c);
        }
        for (Contendiente c : bandoB) {
            estrategia.put(c.id(), b);
            bando.put(c.id(), 'B');
            combatientes.add(c);
        }
        // §6.1.3: orden sorteado al empezar e invariable (Fisher-Yates).
        List<String> orden = new ArrayList<>(estrategia.keySet());
        for (int i = orden.size() - 1; i > 0; i--) {
            int j = azar.nextInt(i + 1);
            String t = orden.get(i);
            orden.set(i, orden.get(j));
            orden.set(j, t);
        }

        Metricas m = new Metricas();
        m.partidas = 1;
        int turno = 0;
        while (ganador(combatientes, bando) == null && turno < topeDeTurnos) {
            String id = orden.get(turno % orden.size());
            turno++;
            if (!vivo(combatientes, id)) {
                continue;
            }
            ResultadoDeTurno inicio = motorDeReferencia.iniciarTurno(
                    new SolicitudDeTurno(id, porEquipos, combatientes), azar);
            combatientes = inicio.combatientes();
            if (!vivo(combatientes, id) || ganador(combatientes, bando) != null) {
                continue;
            }
            ResultadoDeAccion r;
            try {
                r = jugar(estrategia.get(id), id, combatientes, porEquipos, azar);
            } catch (AccionNoPermitida ilegal) {
                m.ilegales++;
                continue;
            }
            contar(m, r, combatientes, porEquipos);
            combatientes = r.combatientes();
        }
        m.turnos = turno;
        Character quien = ganador(combatientes, bando);
        if (quien == null || quien == '-') {
            // Tope de turnos, o los dos bandos cayeron en la misma accion.
            m.tablas = 1;
        } else if (quien == 'A') {
            m.victoriasA = 1;
        } else {
            m.victoriasB = 1;
        }
        return m;
    }

    private ResultadoDeAccion jugar(Estrategia estrategia, String id, List<Contendiente> combatientes,
                                    boolean porEquipos, RandomGenerator azar) {
        if (estrategia.dificultad != null) {
            return motores.get(estrategia.dificultad).resolver(
                    new SolicitudDeAccion(Reglamento.DECISION_DE_LA_MAQUINA, id, null, porEquipos, combatientes),
                    azar);
        }
        Mesa mesa = new Mesa();
        for (Contendiente c : combatientes) {
            mesa.sentar(c, catalogo.ficha(c.prototipo(), c.nivel()));
        }
        PoliticaDeLaMaquina.Decision d =
                PoliticaDeLaMaquina.respaldo(id, mesa, porEquipos, motorDeReferencia.reglamento());
        return motorDeReferencia.resolver(
                new SolicitudDeAccion(d.accion(), id, d.objetivo(), porEquipos, combatientes), azar);
    }

    /**
     * Lo que se cuenta de una accion. Auto-dano: un DANO cuyo destinatario es
     * quien actua, o vida perdida por quien actua sin un REFLEJO que la
     * explique. Fuego amigo: un DANO de quien actua a un companero.
     */
    private static void contar(Metricas m, ResultadoDeAccion r, List<Contendiente> antes, boolean porEquipos) {
        m.acciones++;
        String yo = r.ejecutor();
        String codigo = r.accionEjecutada();
        if (r.esEpica()) {
            m.epicas++;
        } else if (!Reglamento.ATAQUE_BASICO.equals(codigo) && !Reglamento.SANACION_BASICA.equals(codigo)) {
            m.especiales++;
        }
        if (r.tipo() == TipoDeAccion.SANACION || r.tipo() == TipoDeAccion.SANACION_GRUPAL
                || r.tipo() == TipoDeAccion.REANIMACION) {
            m.sanaciones++;
        }
        if (r.tipo() == TipoDeAccion.DEFENSA) {
            m.defensas++;
        }
        Contendiente ejecutor = antes.stream().filter(c -> c.id().equals(yo)).findFirst().orElseThrow();
        boolean reflejo = false;
        for (Evento e : r.eventos()) {
            if (e.tipo() == TipoDeEvento.DANO && e.combatiente().equals(yo)) {
                m.autoDano++;
            }
            if (e.tipo() == TipoDeEvento.REFLEJO && e.combatiente().equals(yo)) {
                reflejo = true;
                m.danoReflejado += e.cantidad() == null ? 0 : e.cantidad();
            }
            if (e.tipo() == TipoDeEvento.DANO && yo.equals(e.origen())) {
                Contendiente golpeado = antes.stream().filter(c -> c.id().equals(e.combatiente())).findFirst()
                        .orElseThrow();
                if (ejecutor.esCompaneroDe(golpeado, porEquipos)) {
                    m.fuegoAmigo++;
                }
            }
        }
        boolean perdioVida = r.afectados().stream().anyMatch(a -> a.id().equals(yo) && a.diferencia() < 0);
        if (perdioVida && !reflejo) {
            m.autoDano++;
        }
    }

    private static boolean vivo(List<Contendiente> combatientes, String id) {
        return combatientes.stream().anyMatch(c -> c.id().equals(id) && c.enPie());
    }

    /** El bando que queda solo en pie, o nulo si siguen dos. */
    private static Character ganador(List<Contendiente> combatientes, Map<String, Character> bando) {
        boolean a = combatientes.stream().anyMatch(c -> c.enPie() && Objects.equals(bando.get(c.id()), 'A'));
        boolean b = combatientes.stream().anyMatch(c -> c.enPie() && Objects.equals(bando.get(c.id()), 'B'));
        if (a && b) {
            return null;
        }
        return a ? Character.valueOf('A') : b ? Character.valueOf('B') : Character.valueOf('-');
    }
}
