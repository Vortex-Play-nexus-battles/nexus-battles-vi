package nexus.misiones.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import nexus.misiones.dominio.CalculadoraDeRecompensas;
import nexus.misiones.dominio.CatalogoDeMisiones;
import nexus.misiones.dominio.Ejecucion;
import nexus.misiones.dominio.EjecucionModificadaConcurrentemente;
import nexus.misiones.dominio.EstadoEjecucion;
import nexus.misiones.dominio.GrupoDeEnemigos;
import nexus.misiones.dominio.HeroeEnMision;
import nexus.misiones.dominio.Jefe;
import nexus.misiones.dominio.MasterDeMision;
import nexus.misiones.dominio.Mision;
import nexus.misiones.dominio.RecompensasDeEjecucion;
import nexus.misiones.dominio.RepositorioDeEjecuciones;
import nexus.misiones.dominio.RepositorioDeEventosDeCombate;
import nexus.misiones.dominio.simulacion.Azar;
import nexus.misiones.dominio.simulacion.AzarConSemilla;
import nexus.misiones.dominio.simulacion.DecisorDeTurno;
import nexus.misiones.dominio.simulacion.MotorDeCombate;
import nexus.misiones.dominio.simulacion.OrigenDeEstrategia;
import nexus.misiones.dominio.simulacion.PerfilDeCombate;
import nexus.misiones.dominio.simulacion.PlanDeCombate;
import nexus.misiones.dominio.simulacion.RefuerzoDeMaster;
import nexus.misiones.dominio.simulacion.ReglaDelMaster;
import nexus.misiones.dominio.simulacion.ResultadoDeMision;
import nexus.misiones.dominio.simulacion.Rival;
import nexus.misiones.dominio.simulacion.Simulacion;
import nexus.misiones.dominio.simulacion.SimuladorDeMision;
import nexus.misiones.dominio.simulacion.TiradaDeMasters;
import nexus.misiones.dominio.simulacion.TipoDeRival;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * La simulacion de una ejecucion cuyo plazo vencio (7.8.6: «al completarse el
 * tiempo de duracion»; 7.8.12: «ejecucion de simulaciones en segundo plano» y
 * «capacidad de simular combates a velocidad acelerada»).
 *
 * <p>Prepara a los rivales —estadisticas de la vista por nivel de heroes, o las
 * de la semilla, escaladas por el escalon—, arma el perfil del heroe (nivel,
 * estadisticas con equipo, equipo y epicas), sortea los Master, simula cada
 * turno contra el motor de combate de las batallas en linea, calcula las
 * recompensas y lo guarda todo de una vez: los turnos de combate (HU-SIM-003)
 * y la ejecucion terminada.
 *
 * <p><b>Velocidad acelerada.</b> La simulacion no espera tiempo real por turno:
 * se ejecuta entera, en lote, en cuanto el plazo vence, y la duracion de la
 * mision solo gobierna CUANDO se puede ver el resultado.
 *
 * <p>Si heroes, el inventario, productos o el motor no responden a mitad, no se
 * guarda nada y la ejecucion espera a la siguiente vuelta del trabajo: la
 * simulacion se repite entera, con la misma semilla. Los turnos se guardan
 * ANTES que la ejecucion y reemplazando lo que dejara un intento anterior:
 * nunca queda una ejecucion terminada sin sus turnos ni turnos de dos intentos
 * mezclados.
 *
 * <h2>Continuidad en segundo plano (HU-SIM-007)</h2>
 *
 * <ul>
 *   <li><b>Una sola vez.</b> Antes de simular se <i>reserva</i> la ejecucion
 *       guardandola con la version leida ({@link Ejecucion#reservarParaSimular}):
 *       de dos barridos o dos instancias que la lean a la vez, solo uno logra
 *       guardar y es el unico que simula. El aplazamiento de los fallos (30 s,
 *       1, 2 y 4 min, tope 5) es el del trabajo ({@link TrabajoDeMisiones}).</li>
 *   <li><b>Si el proceso muere</b> simulando, el arriendo vence a los
 *       {@link Ejecucion#ARRIENDO_DE_SIMULACION} y otra vuelta la retoma; lo
 *       unico que pudo quedar escrito son turnos, y el siguiente intento los
 *       reemplaza. Si el arriendo ya vencio cuando la simulacion termina, no se
 *       escribe nada: otra instancia pudo haberla tomado y sus turnos son los
 *       que valen.</li>
 *   <li><b>Si el jugador cancela</b> mientras se simula, manda la
 *       cancelacion: se descartan los turnos que se alcanzaron a escribir.</li>
 * </ul>
 */
public class SimularEjecucion {

    private static final Logger BITACORA = LoggerFactory.getLogger(SimularEjecucion.class);

    private final CatalogoDeMisiones catalogo;
    private final RepositorioDeEjecuciones ejecuciones;
    private final RepositorioDeEventosDeCombate eventos;
    private final ServicioDeHeroes heroes;
    private final DecisorDeTurno decisor;
    private final MotorDeCombate motor;
    private final PerfilDeCombateDelHeroe perfiles;
    private final EstrategiaDeEnemigos enemigos;
    private final ParametrosDeMisiones parametros;
    private final Clock reloj;
    private final ReglaDelMaster reglaDelMaster;

    /** Con la IA de siempre: las jugadas las decide la regla de heroes. */
    public SimularEjecucion(CatalogoDeMisiones catalogo, RepositorioDeEjecuciones ejecuciones,
                            RepositorioDeEventosDeCombate eventos, ServicioDeHeroes heroes, MotorDeCombate motor,
                            PerfilDeCombateDelHeroe perfiles, EstrategiaDeEnemigos enemigos,
                            ParametrosDeMisiones parametros, Clock reloj) {
        this(catalogo, ejecuciones, eventos, heroes, heroes, motor, perfiles, enemigos, parametros, reloj);
    }

    /** Con la regla del Master publicada en la semilla ({@link ReglaDelMaster#publicada()}). */
    public SimularEjecucion(CatalogoDeMisiones catalogo, RepositorioDeEjecuciones ejecuciones,
                            RepositorioDeEventosDeCombate eventos, ServicioDeHeroes heroes, DecisorDeTurno decisor,
                            MotorDeCombate motor, PerfilDeCombateDelHeroe perfiles, EstrategiaDeEnemigos enemigos,
                            ParametrosDeMisiones parametros, Clock reloj) {
        this(catalogo, ejecuciones, eventos, heroes, decisor, motor, perfiles, enemigos, parametros, reloj,
                ReglaDelMaster.publicada());
    }

    /**
     * @param decisor        quien decide la jugada de cada turno, de los dos lados: la regla de heroes o, con el
     *                       modelo de IA encendido (HU-SIM-008), el decorador que la envuelve
     * @param reglaDelMaster cuanto de la vida y la defensa de su prototipo conserva un Master (HU-SIM-006)
     */
    public SimularEjecucion(CatalogoDeMisiones catalogo, RepositorioDeEjecuciones ejecuciones,
                            RepositorioDeEventosDeCombate eventos, ServicioDeHeroes heroes, DecisorDeTurno decisor,
                            MotorDeCombate motor, PerfilDeCombateDelHeroe perfiles, EstrategiaDeEnemigos enemigos,
                            ParametrosDeMisiones parametros, Clock reloj, ReglaDelMaster reglaDelMaster) {
        this.reglaDelMaster = Objects.requireNonNull(reglaDelMaster);
        this.decisor = Objects.requireNonNull(decisor);
        this.catalogo = Objects.requireNonNull(catalogo);
        this.ejecuciones = Objects.requireNonNull(ejecuciones);
        this.eventos = Objects.requireNonNull(eventos);
        this.heroes = Objects.requireNonNull(heroes);
        this.motor = Objects.requireNonNull(motor);
        this.perfiles = Objects.requireNonNull(perfiles);
        this.enemigos = Objects.requireNonNull(enemigos);
        this.parametros = Objects.requireNonNull(parametros);
        this.reloj = Objects.requireNonNull(reloj);
    }

    /**
     * @param candidata la ejecucion tal como la leyo el barrido; la reserva se anota en ella misma
     * @return la ejecucion terminada y guardada, o vacio si todavia no tocaba, si otra vuelta ya la tomo o si el
     *         jugador la cancelo mientras se simulaba
     * @throws RuntimeException si la simulacion falla; quien llama la aplaza ({@link TrabajoDeMisiones})
     */
    public Optional<Ejecucion> simular(Ejecucion candidata) {
        Instant ahora = reloj.instant();
        if (!candidata.listaParaSimular(ahora)) {
            return Optional.empty();
        }
        Ejecucion ejecucion = reservar(candidata, ahora);
        if (ejecucion == null) {
            return Optional.empty();
        }
        try {
            return simularReservada(ejecucion, ahora);
        } catch (EjecucionModificadaConcurrentemente cambio) {
            return cambioMientrasSeSimulaba(ejecucion);
        }
    }

    /** @return la ejecucion reservada y guardada, o nulo si otra vuelta se adelanto o ya cambio */
    private Ejecucion reservar(Ejecucion candidata, Instant ahora) {
        candidata.reservarParaSimular(ahora);
        try {
            return ejecuciones.guardar(candidata);
        } catch (EjecucionModificadaConcurrentemente otraVuelta) {
            BITACORA.info("La ejecucion {} ya la tomo otra vuelta o cambio de estado: aqui no se simula",
                    candidata.id());
            return null;
        }
    }

    private Optional<Ejecucion> simularReservada(Ejecucion ejecucion, Instant ahora) {
        Optional<Mision> mision = catalogo.buscar(ejecucion.misionId());
        if (mision.isEmpty()) {
            // La semilla ya no publica esa mision: no hay contra que simular.
            // Se termina como fallida y sin recompensas para que el heroe
            // vuelva al inventario, en vez de dejarlo bloqueado para siempre.
            BITACORA.warn("La ejecucion {} es de la mision {}, que ya no esta publicada: se cierra sin simular",
                    ejecucion.id(), ejecucion.misionId());
            ejecucion.terminar(sinCombate(), sinRecompensas(), false, ahora);
            return Optional.of(ejecuciones.guardar(ejecucion));
        }

        Azar azar = new AzarConSemilla(ejecucion.semilla());
        HeroeEnMision heroe = ejecucion.heroe();
        double multiplicador = Optional.ofNullable(parametros.multiplicadorDeEstadisticas(ejecucion.escalon()))
                .orElse(1.0);
        Map<String, ServicioDeHeroes.EstadisticasDeNivel> vistas = new HashMap<>();
        int nivelDeLosEnemigos = nivelDeLosEnemigos(mision.get(), heroe);

        List<Rival> regulares = new ArrayList<>();
        for (GrupoDeEnemigos grupo : mision.get().enemigos()) {
            Rival rival = rival(grupo.nombre(), TipoDeRival.REGULAR, grupo.prototipo(), nivelDeLosEnemigos,
                    grupo.vida(), grupo.defensa(), grupo.rotaciones(), null, multiplicador, vistas, heroe.nivel());
            for (int i = 0; i < grupo.cantidad(); i++) {
                regulares.add(rival);
            }
        }
        List<Rival> masters = new ArrayList<>();
        for (MasterDeMision master : TiradaDeMasters.quienesAparecen(mision.get(), heroe.prototipo(),
                catalogo.tabla20(), azar)) {
            // Primero la regla de equilibrio (HU-SIM-006): el Master conserva solo una fraccion de la vida y la
            // defensa de su prototipo, la que mide que un heroe de ese nivel le gane cerca de la mitad de las veces.
            // Despues «estadisticas superiores a enemigos regulares» (7.8.4): los dos niveles de mas casi siempre
            // bastan, y donde no (tope 8, un prototipo mas debil, dados que no escalan, o una fraccion que lo deja
            // por debajo de un regular) el refuerzo lo sube hasta quedar por encima. El piso manda sobre la regla.
            // Un Master que la semilla fija (vida o defensa, solo el del banco E2E) pelea con lo que dice la
            // semilla: ese valor manda sobre la fraccion y el Master no se refuerza.
            Rival delMaster = rival(master.nombre(), TipoDeRival.MASTER, master.prototipo(),
                    MasterDeMision.nivelFrente(heroe.nivel()), master.vida(), master.defensa(), List.of(), master,
                    multiplicador, vistas, heroe.nivel());
            boolean fijadoPorLaSemilla = master.vida() != null || master.defensa() != null;
            masters.add(fijadoPorLaSemilla ? delMaster : RefuerzoDeMaster.reforzar(delMaster, regulares));
        }
        Jefe jefe = mision.get().jefe();
        Rival rivalFinal = jefe == null ? null
                : rival(jefe.nombre(), TipoDeRival.JEFE, jefe.prototipo(), nivelDeLosEnemigos, jefe.vida(),
                        jefe.defensa(), jefe.rotaciones(), null, multiplicador, vistas, heroe.nivel());

        List<Rival> plan = PlanDeCombate.armar(regulares, masters, rivalFinal, azar);
        PerfilDeCombate perfil = perfiles.de(ejecucion.jugadorUid(), heroe);
        Long semillaDeGolpes = parametros.semillaDePruebas() == null ? null : ejecucion.semilla();
        Simulacion simulacion = new SimuladorDeMision(decisor, motor, heroes).simular(ejecucion.id(),
                mision.get().id(), heroe, perfil, ejecucion.estrategia(), plan, azar, semillaDeGolpes);
        ResultadoDeMision resultado = simulacion.resultado();

        boolean primeraVez = ejecuciones.delJugadorEnMision(ejecucion.jugadorUid(), ejecucion.misionId()).stream()
                .noneMatch(e -> e.estado() == EstadoEjecucion.COMPLETADA && !e.id().equals(ejecucion.id()));
        RecompensasDeEjecucion recompensas = CalculadoraDeRecompensas.calcular(mision.get(), ejecucion.escalon(),
                resultado, primeraVez, parametros.recompensas(), azar);

        if (!ejecucion.reservaVigente(reloj.instant())) {
            // Tardo mas que el arriendo: otra instancia pudo tomarla y escribir sus turnos, y los de este intento
            // los pisarian sin que su resultado llegue a guardarse nunca.
            BITACORA.warn("Ejecucion {}: la simulacion tardo mas que su arriendo ({}); se descarta y la retoma otra"
                    + " vuelta", ejecucion.id(), Ejecucion.ARRIENDO_DE_SIMULACION);
            return Optional.empty();
        }
        eventos.reemplazar(ejecucion.id(), simulacion.eventos());
        // Terminar borra los intentos fallidos y su error (empieza la liquidacion): se anotan antes para la bitacora.
        int fallosPrevios = ejecucion.intentosDeLiquidacion();
        String falloAnterior = ejecucion.ultimoError();
        ejecucion.terminar(resultado, recompensas, parametros.correoActivo(), parametros.avisosActivos(), ahora);
        Ejecucion guardada = ejecuciones.guardar(ejecucion);
        BITACORA.info("Ejecucion {} simulada: {} en {} turnos, {} enemigos derrotados, {} de experiencia",
                guardada.id(), guardada.estado(), resultado.turnos(), resultado.encuentrosCompletados(),
                String.format(java.util.Locale.ROOT, "%.2f", recompensas.experiencia()));
        if (fallosPrevios > 0) {
            BITACORA.info("Ejecucion {} simulada tras {} intentos fallidos: se recupero del fallo anterior ({})",
                    guardada.id(), fallosPrevios, falloAnterior);
        }
        return Optional.of(guardada);
    }

    /**
     * Al guardar el final la ejecucion ya no estaba en la version reservada: o el jugador la cancelo mientras se
     * simulaba (manda la cancelacion y los turnos escritos se descartan) o, vencido el arriendo, otra vuelta la
     * termino por su lado y sus turnos son los que valen.
     */
    private Optional<Ejecucion> cambioMientrasSeSimulaba(Ejecucion ejecucion) {
        Optional<Ejecucion> actual = ejecuciones.buscar(ejecucion.id());
        if (actual.isEmpty() || actual.get().estado() == EstadoEjecucion.ABANDONADA) {
            BITACORA.info("Ejecucion {}: el jugador la cancelo mientras se simulaba; se descartan los turnos",
                    ejecucion.id());
            try {
                eventos.reemplazar(ejecucion.id(), List.of());
            } catch (RuntimeException sinLimpiar) {
                BITACORA.warn("Ejecucion {}: no se pudieron descartar los turnos de la simulacion cancelada: {}",
                        ejecucion.id(), sinLimpiar.getMessage());
            }
        } else {
            BITACORA.info("Ejecucion {} cambio mientras se simulaba ({}): se deja lo que guardo la otra vuelta",
                    ejecucion.id(), actual.get().estado());
        }
        return Optional.empty();
    }

    /**
     * El nivel de los enemigos regulares y del jefe — §7.8.13: «las
     * estadisticas de enemigos deben escalar segun nivel de mision» (D-42). Es
     * el nivel recomendado de la mision; solo la que no lo dice (la provisional
     * de DEV) pelea en el nivel del heroe, como hasta ahora. Antes TODAS
     * peleaban en el nivel del heroe: subir de nivel no hacia mas facil
     * ninguna mision, y el Templo, con un nivel recomendado de 15 que ningun
     * heroe alcanza (§6.1.1: hasta 8), no tenia nivel en el que jugarse.
     */
    static int nivelDeLosEnemigos(Mision mision, HeroeEnMision heroe) {
        return mision.nivelRecomendado() != null ? mision.nivelRecomendado() : heroe.nivel();
    }

    /**
     * Un rival listo para el combate. Lo que la semilla no fija sale de la
     * vista por nivel de heroes (una llamada por prototipo y nivel, no por
     * enemigo); el escalon multiplica vida y defensa (7.8.11: «enemigos con 50%
     * mas estadisticas»). Las formulas de ataque y dano son las de esa vista:
     * con ellas el motor pelea con la vida y la defensa de la semilla y del
     * escalon, y no con las del catalogo. La estrategia, por precedencia (HU-SIM-004): la rotacion que la
     * mision trae escrita para ese enemigo; si no, la predefinida de su prototipo y nivel; y si tampoco, la
     * heuristica por defecto ({@link EstrategiaDeEnemigos}). El rival lleva de cual salio.
     *
     * <p>Un Master conserva solo la fraccion de la vida y la defensa de su prototipo que la regla fija para el nivel
     * del heroe ({@link ReglaDelMaster}); el escalon multiplica despues, y el piso de {@link RefuerzoDeMaster}, al final.
     */
    private Rival rival(String nombre, TipoDeRival tipo, String prototipo, int nivel, Integer vida, Integer defensa,
                        List<List<String>> rotaciones, MasterDeMision master, double multiplicador,
                        Map<String, ServicioDeHeroes.EstadisticasDeNivel> vistas, int nivelDelHeroe) {
        ServicioDeHeroes.EstadisticasDeNivel vista = vistas.computeIfAbsent(prototipo + "@" + nivel,
                clave -> heroes.enNivel(prototipo, nivel));
        int vidaBase = vida != null ? vida : vista.vida();
        int defensaBase = defensa != null ? defensa : vista.defensa();
        if (tipo == TipoDeRival.MASTER) {
            // Lo que la semilla fija manda sobre la fraccion: solo se reduce lo que sale del prototipo.
            if (vida == null) {
                vidaBase = reglaDelMaster.vida(vidaBase, nivelDelHeroe);
            }
            if (defensa == null) {
                defensaBase = reglaDelMaster.defensa(defensaBase, nivelDelHeroe);
            }
        }
        EstrategiaDeEnemigos.Elegida estrategia = rotaciones.isEmpty()
                ? enemigos.elegir(prototipo, nivel)
                : new EstrategiaDeEnemigos.Elegida(OrigenDeEstrategia.MISION, null, rotaciones);
        return new Rival(nombre, tipo, prototipo, nivel,
                Math.max(1, (int) Math.round(vidaBase * multiplicador)),
                (int) Math.round(defensaBase * multiplicador),
                vista.poder(), estrategia.rotaciones(), master == null ? null : master.epica(),
                vista.ataque(), vista.dano(), vista.sanar(), estrategia.origen(), estrategia.id());
    }

    private static ResultadoDeMision sinCombate() {
        return new ResultadoDeMision(false, false, 0, false, 0, 0, 0, 0, List.of(), List.of(), List.of(), 100, 0,
                List.of());
    }

    private static RecompensasDeEjecucion sinRecompensas() {
        return new RecompensasDeEjecucion(0, List.of(), List.of(), 0, List.of(), List.of(), false);
    }
}
