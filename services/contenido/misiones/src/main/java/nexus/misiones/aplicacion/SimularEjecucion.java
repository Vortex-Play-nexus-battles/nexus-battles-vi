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

    /** Con la IA de siempre: las jugadas las decide la regla de heroes. */
    public SimularEjecucion(CatalogoDeMisiones catalogo, RepositorioDeEjecuciones ejecuciones,
                            RepositorioDeEventosDeCombate eventos, ServicioDeHeroes heroes, MotorDeCombate motor,
                            PerfilDeCombateDelHeroe perfiles, EstrategiaDeEnemigos enemigos,
                            ParametrosDeMisiones parametros, Clock reloj) {
        this(catalogo, ejecuciones, eventos, heroes, heroes, motor, perfiles, enemigos, parametros, reloj);
    }

    /**
     * @param decisor quien decide la jugada de cada turno, de los dos lados: la regla de heroes o, con el modelo de
     *                IA encendido (HU-SIM-008), el decorador que la envuelve
     */
    public SimularEjecucion(CatalogoDeMisiones catalogo, RepositorioDeEjecuciones ejecuciones,
                            RepositorioDeEventosDeCombate eventos, ServicioDeHeroes heroes, DecisorDeTurno decisor,
                            MotorDeCombate motor, PerfilDeCombateDelHeroe perfiles, EstrategiaDeEnemigos enemigos,
                            ParametrosDeMisiones parametros, Clock reloj) {
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

    /** @return la ejecucion terminada y guardada, o vacio si todavia no tocaba */
    public Optional<Ejecucion> simular(Ejecucion ejecucion) {
        Instant ahora = reloj.instant();
        if (ejecucion.estado() != EstadoEjecucion.EN_PROGRESO || !ejecucion.vencida(ahora)) {
            return Optional.empty();
        }
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
                    grupo.vida(), grupo.defensa(), grupo.rotaciones(), null, multiplicador, vistas);
            for (int i = 0; i < grupo.cantidad(); i++) {
                regulares.add(rival);
            }
        }
        List<Rival> masters = new ArrayList<>();
        for (MasterDeMision master : TiradaDeMasters.quienesAparecen(mision.get(), heroe.prototipo(),
                catalogo.tabla20(), azar)) {
            masters.add(rival(master.nombre(), TipoDeRival.MASTER, master.prototipo(),
                    MasterDeMision.nivelFrente(heroe.nivel()), master.vida(), master.defensa(), List.of(), master,
                    multiplicador, vistas));
        }
        Jefe jefe = mision.get().jefe();
        Rival rivalFinal = jefe == null ? null
                : rival(jefe.nombre(), TipoDeRival.JEFE, jefe.prototipo(), nivelDeLosEnemigos, jefe.vida(),
                        jefe.defensa(), jefe.rotaciones(), null, multiplicador, vistas);

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

        eventos.reemplazar(ejecucion.id(), simulacion.eventos());
        ejecucion.terminar(resultado, recompensas, parametros.correoActivo(), parametros.avisosActivos(), ahora);
        Ejecucion guardada = ejecuciones.guardar(ejecucion);
        BITACORA.info("Ejecucion {} simulada: {} en {} turnos, {} enemigos derrotados, {} de experiencia",
                guardada.id(), guardada.estado(), resultado.turnos(), resultado.encuentrosCompletados(),
                String.format(java.util.Locale.ROOT, "%.2f", recompensas.experiencia()));
        return Optional.of(guardada);
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
     */
    private Rival rival(String nombre, TipoDeRival tipo, String prototipo, int nivel, Integer vida, Integer defensa,
                        List<List<String>> rotaciones, MasterDeMision master, double multiplicador,
                        Map<String, ServicioDeHeroes.EstadisticasDeNivel> vistas) {
        ServicioDeHeroes.EstadisticasDeNivel vista = vistas.computeIfAbsent(prototipo + "@" + nivel,
                clave -> heroes.enNivel(prototipo, nivel));
        int vidaBase = vida != null ? vida : vista.vida();
        int defensaBase = defensa != null ? defensa : vista.defensa();
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
