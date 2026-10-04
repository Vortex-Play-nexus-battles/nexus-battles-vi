package nexus.misiones.dominio.simulacion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
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
 *   <li>Cada turno y cada accion los resuelve el MOTOR DE COMBATE
 *       ({@link MotorDeCombate}), el mismo de las batallas en linea
 *       (7.8.12): el poder que se recupera, las cargas, las 24 acciones de la
 *       Tabla 7, las epicas, los efectos del equipo y el sorteo sobre la tabla
 *       de 8.000 filas (6.1.4). Aqui no hay un segundo motor: el simulador
 *       solo guarda el estado que el motor devuelve y se lo manda de nuevo.</li>
 *   <li>Lo que SI es de aqui: el orden de los encuentros, quien empieza cada
 *       duelo («el orden de los turnos para la primera ronda se determina
 *       aleatoriamente», 6.1.3), la vida que el heroe arrastra de un
 *       encuentro al siguiente, la experiencia por enemigo derrotado con el
 *       1d8 que tira el servidor, lo que se cuenta para el reporte (7.8.8) y el
 *       registro de cada turno ({@link EventoDeCombate}).</li>
 * </ul>
 *
 * <p>La vida NO se recupera entre encuentros: el documento no dice que lo haga,
 * y el objetivo «completar la mision sin que la vida del heroe baje del 50 %»
 * (7.8.14) solo tiene sentido si la vida se arrastra. El poder, las cargas y
 * los efectos si empiezan de cero en cada duelo: «el poder se recupera
 * instantaneamente al concluir el combate» (6.1.1).
 *
 * <h2>Cuando el motor rechaza la jugada</h2>
 *
 * <p>La recarga de heroes (HU-HER-007) y la del motor cuentan los turnos cada
 * una a su manera; si el motor responde 409 a la accion que eligio la IA, no
 * se rompe nada: se anota el rechazo, se le dice al decisor que esa opcion ya
 * no esta disponible en este turno y se le pregunta de nuevo, asi que cae a la
 * siguiente de su rotacion y, al final, al ataque basico. Una simulacion nunca
 * se queda a medias por esto.
 */
public class SimuladorDeMision {

    /** El id del heroe y del rival en cada duelo, tal como se los manda al motor. */
    public static final String ID_DEL_HEROE = "heroe";
    public static final String ID_DEL_RIVAL = "rival";

    /**
     * Un duelo que nadie puede ganar (ninguno supera la defensa del otro) se
     * corta aqui y la mision se da por fallida: el heroe no pudo vencer. Sin
     * este tope, una ejecucion asi giraria para siempre dentro del trabajo en
     * segundo plano.
     */
    public static final int RONDAS_MAXIMAS_POR_COMBATE = 100;

    private static final int HABILIDADES_EN_EL_REPORTE = 5;

    /** Las tres rotaciones de una estrategia (7.8.5) y el ataque basico de respaldo. */
    private static final int OPCIONES_MAXIMAS_POR_TURNO = 4;

    private static final String SANACION_BASICA = "Sanación básica";

    private final DecisorDeTurno decisor;
    private final MotorDeCombate motor;
    private final TablaDeExperiencia experiencia;

    public SimuladorDeMision(DecisorDeTurno decisor, MotorDeCombate motor, TablaDeExperiencia experiencia) {
        this.decisor = Objects.requireNonNull(decisor);
        this.motor = Objects.requireNonNull(motor);
        this.experiencia = Objects.requireNonNull(experiencia);
    }

    /**
     * @param ejecucionId     la ejecucion que se simula: los eventos quedan a su nombre
     * @param perfil          lo que el heroe lleva al combate (estadisticas con equipo, equipo, epicas)
     * @param estrategia      las rotaciones del heroe, ya validadas por heroes
     * @param rivales         en el orden en que se enfrentan (el jefe al final)
     * @param azar            la fuente de azar de esta ejecucion
     * @param semillaDeGolpes solo en pruebas reproducibles: cada llamada al
     *                        motor lleva la semilla siguiente; nula en juego real
     */
    public Simulacion simular(UUID ejecucionId, String misionId, HeroeEnMision heroe, PerfilDeCombate perfil,
                              List<List<String>> estrategia, List<Rival> rivales, Azar azar, Long semillaDeGolpes) {
        int regulares = (int) rivales.stream().filter(r -> r.tipo() == TipoDeRival.REGULAR).count();
        Registro registro = new Registro(ejecucionId, misionId, heroe, perfil, semillaDeGolpes, regulares);
        int vidaDelHeroe = registro.vidaMaximaDelHeroe;

        int encuentro = 0;
        for (Rival rival : rivales) {
            encuentro++;
            Duelo duelo = new Duelo(heroe, perfil, estrategia, rival, encuentro, vidaDelHeroe, registro);
            duelo.jugar(azar.acierta(0.5));
            vidaDelHeroe = duelo.vidaDelHeroe();
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

    /** Uno de los dos que pelean en un duelo, con lo que la IA recuerda de el entre turno y turno. */
    private static final class Bando {
        final String id;
        final EventoDeCombate.Actor actor;
        final List<List<String>> rotaciones;
        /** El heroe siempre pregunta al decisor; un rival sin estrategia juega el ataque basico sin preguntar. */
        final boolean consultaAlDecisor;
        final Map<String, Integer> usos = new HashMap<>();
        List<Integer> cursores = List.of();

        Bando(String id, EventoDeCombate.Actor actor, List<List<String>> rotaciones, boolean consultaAlDecisor) {
            this.id = id;
            this.actor = actor;
            this.rotaciones = rotaciones == null ? List.of() : rotaciones;
            this.consultaAlDecisor = consultaAlDecisor;
        }
    }

    /** Un combate uno contra uno. El heroe llega con la vida que le quedaba. */
    private final class Duelo {

        private final Rival rival;
        private final int encuentro;
        private final Registro registro;
        private final Bando elHeroe;
        private final Bando elRival;

        /** El estado de los dos, tal como lo devolvio el motor la ultima vez. */
        private List<Combatiente> mesa;

        Duelo(HeroeEnMision heroe, PerfilDeCombate perfil, List<List<String>> estrategia, Rival rival, int encuentro,
              int vidaDelHeroe, Registro registro) {
            this.rival = rival;
            this.encuentro = encuentro;
            this.registro = registro;
            this.elHeroe = new Bando(ID_DEL_HEROE,
                    new EventoDeCombate.Actor(EventoDeCombate.Lado.HEROE, heroe.nombre(), heroe.prototipo(),
                            heroe.nivel()),
                    estrategia, true);
            this.elRival = new Bando(ID_DEL_RIVAL,
                    new EventoDeCombate.Actor(ladoDe(rival.tipo()), rival.nombre(), rival.prototipo(), rival.nivel()),
                    rival.rotaciones(), !rival.rotaciones().isEmpty());
            // «El poder se recupera instantaneamente al concluir el combate»:
            // cada duelo empieza con el poder al maximo (poder nulo), sin
            // cargas ni efectos y con las rotaciones en su primer paso.
            this.mesa = List.of(
                    Combatiente.alEmpezar(ID_DEL_HEROE, heroe.prototipo(), heroe.nivel(), perfil.estadisticas(),
                            vidaDelHeroe, perfil.equipamiento(), perfil.epicas()),
                    Combatiente.alEmpezar(ID_DEL_RIVAL, rival.prototipo(), rival.nivel(), estadisticasDelRival(),
                            rival.vida(), List.of(), List.of()));
        }

        /**
         * Con las formulas del catalogo en la mano, el rival entra con la vida y
         * la defensa de la semilla (y del escalon); sin ellas, con las del
         * catalogo, porque una estadistica sin formula de ataque no podria golpear.
         */
        private EstadisticasDeCombate estadisticasDelRival() {
            if (rival.ataque() == null && rival.sanar() == null) {
                return null;
            }
            return new EstadisticasDeCombate(rival.poder(), rival.vida(), rival.defensa(), rival.ataque(),
                    rival.dano(), rival.sanar());
        }

        void jugar(boolean empiezaElHeroe) {
            for (int ronda = 1; ronda <= RONDAS_MAXIMAS_POR_COMBATE && ambosEnPie(); ronda++) {
                registro.turnos++;
                if (empiezaElHeroe) {
                    turnoDe(elHeroe, elRival, ronda);
                    turnoDe(elRival, elHeroe, ronda);
                } else {
                    turnoDe(elRival, elHeroe, ronda);
                    turnoDe(elHeroe, elRival, ronda);
                }
            }
        }

        int vidaDelHeroe() {
            return combatiente(ID_DEL_HEROE).vidaActual();
        }

        boolean rivalDerrotado() {
            return !combatiente(ID_DEL_RIVAL).enPie();
        }

        private boolean ambosEnPie() {
            return combatiente(ID_DEL_HEROE).enPie() && combatiente(ID_DEL_RIVAL).enPie();
        }

        private Combatiente combatiente(String id) {
            return mesa.stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
        }

        private void actualizar(List<Combatiente> nuevos) {
            mesa = List.copyOf(nuevos);
            registro.vidaMinima = Math.min(registro.vidaMinima, Math.max(0, combatiente(ID_DEL_HEROE).vidaActual()));
        }

        /** El turno de un combatiente: empezarlo (motor), decidir (heroes), jugarlo (motor) y dejar constancia. */
        private void turnoDe(Bando quien, Bando contra, int ronda) {
            if (!ambosEnPie()) {
                return;
            }
            ResultadoDeTurno inicio = motor.iniciarTurno(quien.id, mesa, registro.siguienteSemilla());
            actualizar(inicio.combatientes());
            List<Suceso> alIniciar = traducir(inicio.sucesos());
            EventoDeCombate.Estados antes = estados(quien, contra);

            EventoDeCombate.Jugada jugada = null;
            if (combatiente(quien.id).enPie()) {
                jugada = jugar(quien, contra, ronda);
            }
            registro.evento(encuentro, rival.nombre(), ronda, quien.actor, antes, alIniciar, jugada,
                    estados(quien, contra));
        }

        private EventoDeCombate.Jugada jugar(Bando quien, Bando contra, int ronda) {
            Combatiente actor = combatiente(quien.id);
            String basica = actor.acciones().contains(MotorDeCombate.SANACION_BASICA)
                    && !actor.acciones().contains(MotorDeCombate.ATAQUE_BASICO)
                    ? MotorDeCombate.SANACION_BASICA : MotorDeCombate.ATAQUE_BASICO;
            int poderAntes = poderDe(actor);

            Map<String, Integer> usosDeEsteTurno = new HashMap<>(quien.usos);
            Set<String> descartadas = new HashSet<>();
            List<EventoDeCombate.Rechazo> rechazadas = new ArrayList<>();
            ResultadoDeAccion resultado = null;
            DecisionDeTurno decision = null;
            boolean forzadaALaBasica = false;
            boolean intentoLaBasica = false;

            for (int intento = 0; intento < OPCIONES_MAXIMAS_POR_TURNO && resultado == null; intento++) {
                decision = decidir(quien, actor, ronda, usosDeEsteTurno);
                // Un decisor que insiste en una opcion que el motor ya rechazo
                // este turno no puede colgar la simulacion: ataque basico.
                forzadaALaBasica = !decision.esAtaqueBasico() && descartadas.contains(decision.accion());
                boolean esBasica = decision.esAtaqueBasico() || forzadaALaBasica;
                resultado = intentar(esBasica ? basica : decision.accion(), quien, contra, rechazadas);
                if (resultado == null) {
                    if (esBasica) {
                        intentoLaBasica = true;
                        break;
                    }
                    descartadas.add(decision.accion());
                    // Para el decisor, la opcion rechazada ya se uso este turno: pasa a la siguiente.
                    usosDeEsteTurno.put(decision.accion(), ronda);
                }
            }
            if (resultado == null && !intentoLaBasica) {
                forzadaALaBasica = true;
                resultado = intentar(basica, quien, contra, rechazadas);
            }

            if (resultado == null) {
                // Ni el ataque basico fue admitido: el turno se pierde, la
                // simulacion sigue (el tope de rondas la corta si no hay salida).
                return new EventoDeCombate.Jugada(nombreDe(decision.accion()), null, false, decision.costoDePoder(),
                        0, rechazadas, null);
            }

            actualizar(resultado.combatientes());
            if (!forzadaALaBasica) {
                quien.cursores = decision.cursoresSiguientes();
            }
            String ejecutada = nombreDe(resultado.accionEjecutada());
            // Solo lo que el motor de verdad ejecuto entra en recarga: una
            // accion jugada en valor base no gasto poder ni quedo en carga.
            if (!resultado.enValorBase() && !esBasica(resultado.accionEjecutada())) {
                quien.usos.put(resultado.accionEjecutada(), ronda);
            }
            contar(quien, resultado, ejecutada);
            return new EventoDeCombate.Jugada(nombreDe(decision.accion()), ejecutada, resultado.enValorBase(),
                    decision.costoDePoder(), Math.max(0, poderAntes - poderDe(combatiente(quien.id))), rechazadas,
                    resultadoDe(resultado));
        }

        /** Pide la accion al motor; si la rechaza (409) deja constancia y devuelve nulo. */
        private ResultadoDeAccion intentar(String codigo, Bando quien, Bando contra,
                                           List<EventoDeCombate.Rechazo> rechazadas) {
            try {
                return motor.resolverAccion(codigo, quien.id, contra.id, mesa, registro.siguienteSemilla());
            } catch (AccionNoPermitida rechazo) {
                rechazadas.add(new EventoDeCombate.Rechazo(nombreDe(codigo), rechazo.motivo()));
                return null;
            }
        }

        private DecisionDeTurno decidir(Bando quien, Combatiente actor, int ronda, Map<String, Integer> usos) {
            if (!quien.consultaAlDecisor) {
                // «La IA controla a los enemigos con estrategias predefinidas»
                // (7.8.6). Sin estrategia, su jugada es el ataque basico y no
                // hace falta preguntarla.
                return new DecisionDeTurno(DecisionDeTurno.ATAQUE_BASICO, 0, List.of());
            }
            return decisor.decidir(new TurnoParaDecidir(quien.actor.prototipo(), quien.actor.nivel(), quien.rotaciones,
                    ronda, poderDe(actor), actor.vidaActual(), usos, quien.cursores));
        }

        /** Lo que se cuenta para el reporte (7.8.8): el dano y los criticos, y las habilidades del heroe. */
        private void contar(Bando quien, ResultadoDeAccion resultado, String ejecutada) {
            boolean esElHeroe = quien == elHeroe;
            DetalleDeAtaque golpe = resultado.ataque();
            if (golpe != null) {
                if (esElHeroe) {
                    registro.danoInfligido += golpe.danoAplicado();
                    if (golpe.critico()) {
                        registro.criticos++;
                    }
                } else {
                    registro.danoRecibido += golpe.danoAplicado();
                }
            }
            if (esElHeroe) {
                registro.habilidadUsada(ejecutada);
            }
        }

        // ------------------------------------------------ lo que queda en el evento

        private EventoDeCombate.Estados estados(Bando quien, Bando contra) {
            return new EventoDeCombate.Estados(estadoDe(combatiente(quien.id)), estadoDe(combatiente(contra.id)));
        }

        private EventoDeCombate.EstadoDeCombatiente estadoDe(Combatiente c) {
            EstadisticasDeCombate e = c.estadisticas();
            int vidaMaxima = e == null ? Math.max(1, c.vidaActual()) : e.vida();
            int poderMaximo = e == null ? poderDe(c) : e.poder();
            List<EventoDeCombate.Recarga> recargas = c.recargas().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(r -> new EventoDeCombate.Recarga(r.getKey(), r.getValue())).toList();
            List<EventoDeCombate.EfectoVigente> efectos = c.efectos().stream()
                    .map(f -> new EventoDeCombate.EfectoVigente(f.nombre(), f.tipo(), f.valor(), f.turnos())).toList();
            return new EventoDeCombate.EstadoDeCombatiente(c.vidaActual(), vidaMaxima, poderDe(c), poderMaximo,
                    recargas, efectos);
        }

        private EventoDeCombate.Resultado resultadoDe(ResultadoDeAccion r) {
            DetalleDeAtaque a = r.ataque();
            List<Suceso> sucesos = traducir(r.sucesos());
            if (a == null) {
                return new EventoDeCombate.Resultado(null, null, null, null, null, null, null, false, sucesos);
            }
            return new EventoDeCombate.Resultado(a.categoria(), a.acierta(), a.ataqueResuelto(), a.defensaObjetivo(),
                    a.porcentajeDano(), a.danoBase(), a.danoAplicado(), a.critico(), sucesos);
        }

        /** Los sucesos hablan de HEROE, ENEMIGO, JEFE o MASTER, no de los ids que se le mandaron al motor. */
        private List<Suceso> traducir(List<Suceso> sucesos) {
            return sucesos.stream().map(s -> new Suceso(s.tipo(), lado(s.combatiente()), lado(s.origen()),
                    s.efecto(), s.cantidad())).toList();
        }

        private String lado(String id) {
            if (ID_DEL_HEROE.equals(id)) {
                return EventoDeCombate.Lado.HEROE.name();
            }
            if (ID_DEL_RIVAL.equals(id)) {
                return elRival.actor.lado().name();
            }
            return id;
        }
    }

    private static EventoDeCombate.Lado ladoDe(TipoDeRival tipo) {
        return switch (tipo) {
            case REGULAR -> EventoDeCombate.Lado.ENEMIGO;
            case MASTER -> EventoDeCombate.Lado.MASTER;
            case JEFE -> EventoDeCombate.Lado.JEFE;
        };
    }

    private static int poderDe(Combatiente c) {
        if (c.poderActual() != null) {
            return c.poderActual();
        }
        return c.estadisticas() == null ? 0 : c.estadisticas().poder();
    }

    private static boolean esBasica(String codigo) {
        return MotorDeCombate.ATAQUE_BASICO.equals(codigo) || MotorDeCombate.SANACION_BASICA.equals(codigo);
    }

    /** El nombre legible de una accion: las basicas del motor se llaman como las llama heroes. */
    private static String nombreDe(String codigo) {
        if (MotorDeCombate.ATAQUE_BASICO.equals(codigo)) {
            return DecisionDeTurno.ATAQUE_BASICO;
        }
        if (MotorDeCombate.SANACION_BASICA.equals(codigo)) {
            return SANACION_BASICA;
        }
        return codigo;
    }

    /** Lo que se va contando para el reporte y los turnos que se van registrando. */
    private static final class Registro {

        private final UUID ejecucionId;
        private final String misionId;
        private final Long semillaDeGolpes;
        private final int regularesTotales;
        final int vidaMaximaDelHeroe;
        private long llamadasAlMotor;
        private int secuencia;

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
        final List<EventoDeCombate> eventos = new ArrayList<>();
        int regularesDerrotados;

        Registro(UUID ejecucionId, String misionId, HeroeEnMision heroe, PerfilDeCombate perfil,
                 Long semillaDeGolpes, int regularesTotales) {
            this.ejecucionId = Objects.requireNonNull(ejecucionId);
            this.misionId = misionId;
            this.semillaDeGolpes = semillaDeGolpes;
            this.regularesTotales = regularesTotales;
            // La vida con que sale el heroe: la de sus estadisticas con equipo; si no se conocen, la de la foto.
            this.vidaMaximaDelHeroe = perfil.estadisticas() != null ? perfil.estadisticas().vida() : heroe.vida();
            this.vidaMinima = vidaMaximaDelHeroe;
        }

        /** Cada llamada al motor lleva la semilla siguiente, para que una prueba reproduzca el combate. */
        Long siguienteSemilla() {
            if (semillaDeGolpes == null) {
                return null;
            }
            return semillaDeGolpes + llamadasAlMotor++;
        }

        void habilidadUsada(String accion) {
            usos.merge(accion, 1, Integer::sum);
        }

        void evento(int encuentro, String enemigo, int turno, EventoDeCombate.Actor actor,
                    EventoDeCombate.Estados antes, List<Suceso> alIniciar, EventoDeCombate.Jugada jugada,
                    EventoDeCombate.Estados despues) {
            eventos.add(new EventoDeCombate(ejecucionId, misionId, ++secuencia, encuentro, enemigo, turno, actor,
                    antes, alIniciar, jugada, despues));
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
        Simulacion cerrar(boolean exito) {
            boolean regularesCompletos = regularesDerrotados == regularesTotales;
            List<ResultadoDeMision.UsoDeHabilidad> habilidades = usos.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                    .limit(HABILIDADES_EN_EL_REPORTE)
                    .map(e -> new ResultadoDeMision.UsoDeHabilidad(e.getKey(), e.getValue()))
                    .toList();
            List<ResultadoDeMision.EnemigoDerrotado> enemigos = derrotados.entrySet().stream()
                    .map(e -> new ResultadoDeMision.EnemigoDerrotado(e.getKey(), e.getValue()))
                    .toList();
            int porcentaje = (int) Math.floor(100.0 * vidaMinima / vidaMaximaDelHeroe);
            porcentaje = Math.max(0, Math.min(100, porcentaje));
            ResultadoDeMision resultado = new ResultadoDeMision(exito, jefeDerrotado, encuentrosCompletados,
                    regularesCompletos, danoInfligido, danoRecibido, turnos, criticos, habilidades, enemigos, masters,
                    porcentaje, experiencia, dados);
            return new Simulacion(resultado, eventos);
        }
    }
}
