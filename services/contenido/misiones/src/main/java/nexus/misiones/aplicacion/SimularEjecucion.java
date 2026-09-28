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
import nexus.misiones.dominio.simulacion.Azar;
import nexus.misiones.dominio.simulacion.AzarConSemilla;
import nexus.misiones.dominio.simulacion.PlanDeCombate;
import nexus.misiones.dominio.simulacion.ResolutorDeGolpes;
import nexus.misiones.dominio.simulacion.ResultadoDeMision;
import nexus.misiones.dominio.simulacion.Rival;
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
 * de la semilla, escaladas por el escalon—, sortea los Master, simula, calcula
 * las recompensas y lo guarda todo de una vez. Si heroes o el motor no
 * responden a mitad, no se guarda nada y la ejecucion espera a la siguiente
 * vuelta del trabajo: la simulacion se repite entera, con la misma semilla.
 */
public class SimularEjecucion {

    private static final Logger BITACORA = LoggerFactory.getLogger(SimularEjecucion.class);

    private final CatalogoDeMisiones catalogo;
    private final RepositorioDeEjecuciones ejecuciones;
    private final ServicioDeHeroes heroes;
    private final ResolutorDeGolpes motor;
    private final ParametrosDeMisiones parametros;
    private final Clock reloj;

    public SimularEjecucion(CatalogoDeMisiones catalogo, RepositorioDeEjecuciones ejecuciones,
                            ServicioDeHeroes heroes, ResolutorDeGolpes motor, ParametrosDeMisiones parametros,
                            Clock reloj) {
        this.catalogo = Objects.requireNonNull(catalogo);
        this.ejecuciones = Objects.requireNonNull(ejecuciones);
        this.heroes = Objects.requireNonNull(heroes);
        this.motor = Objects.requireNonNull(motor);
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

        List<Rival> regulares = new ArrayList<>();
        for (GrupoDeEnemigos grupo : mision.get().enemigos()) {
            Rival rival = rival(grupo.nombre(), TipoDeRival.REGULAR, grupo.prototipo(), heroe.nivel(),
                    grupo.vida(), grupo.defensa(), grupo.rotaciones(), null, multiplicador, vistas);
            for (int i = 0; i < grupo.cantidad(); i++) {
                regulares.add(rival);
            }
        }
        List<Rival> masters = new ArrayList<>();
        for (MasterDeMision master : TiradaDeMasters.quienesAparecen(mision.get(), heroe.prototipo(),
                catalogo.tabla20(), azar)) {
            masters.add(rival(master.nombre(), TipoDeRival.MASTER, master.prototipo(),
                    MasterDeMision.nivelFrente(heroe.nivel()), null, null, List.of(), master, multiplicador, vistas));
        }
        Jefe jefe = mision.get().jefe();
        Rival rivalFinal = jefe == null ? null
                : rival(jefe.nombre(), TipoDeRival.JEFE, jefe.prototipo(), heroe.nivel(), jefe.vida(),
                        jefe.defensa(), jefe.rotaciones(), null, multiplicador, vistas);

        List<Rival> plan = PlanDeCombate.armar(regulares, masters, rivalFinal, azar);
        Long semillaDeGolpes = parametros.semillaDePruebas() == null ? null : ejecucion.semilla();
        ResultadoDeMision resultado = new SimuladorDeMision(heroes, motor, heroes)
                .simular(heroe, ejecucion.estrategia(), plan, azar, semillaDeGolpes);

        boolean primeraVez = ejecuciones.delJugadorEnMision(ejecucion.jugadorUid(), ejecucion.misionId()).stream()
                .noneMatch(e -> e.estado() == EstadoEjecucion.COMPLETADA && !e.id().equals(ejecucion.id()));
        RecompensasDeEjecucion recompensas = CalculadoraDeRecompensas.calcular(mision.get(), ejecucion.escalon(),
                resultado, primeraVez, parametros.recompensas(), azar);

        ejecucion.terminar(resultado, recompensas, parametros.correoActivo(), ahora);
        Ejecucion guardada = ejecuciones.guardar(ejecucion);
        BITACORA.info("Ejecucion {} simulada: {} en {} turnos, {} enemigos derrotados, {} de experiencia",
                guardada.id(), guardada.estado(), resultado.turnos(), resultado.encuentrosCompletados(),
                String.format(java.util.Locale.ROOT, "%.2f", recompensas.experiencia()));
        return Optional.of(guardada);
    }

    /**
     * Un rival listo para el combate. Lo que la semilla no fija sale de la
     * vista por nivel de heroes (una llamada por prototipo y nivel, no por
     * enemigo); el escalon multiplica vida y defensa (7.8.11: «enemigos con 50%
     * mas estadisticas»). El ataque lo resuelve el motor con su formula.
     */
    private Rival rival(String nombre, TipoDeRival tipo, String prototipo, int nivel, Integer vida, Integer defensa,
                        List<List<String>> rotaciones, MasterDeMision master, double multiplicador,
                        Map<String, ServicioDeHeroes.EstadisticasDeNivel> vistas) {
        ServicioDeHeroes.EstadisticasDeNivel vista = vistas.computeIfAbsent(prototipo + "@" + nivel,
                clave -> heroes.enNivel(prototipo, nivel));
        int vidaBase = vida != null ? vida : vista.vida();
        int defensaBase = defensa != null ? defensa : vista.defensa();
        return new Rival(nombre, tipo, prototipo, nivel,
                Math.max(1, (int) Math.round(vidaBase * multiplicador)),
                (int) Math.round(defensaBase * multiplicador),
                vista.poder(), rotaciones, master == null ? null : master.epica());
    }

    private static ResultadoDeMision sinCombate() {
        return new ResultadoDeMision(false, false, 0, false, 0, 0, 0, 0, List.of(), List.of(), List.of(), 100, 0,
                List.of());
    }

    private static RecompensasDeEjecucion sinRecompensas() {
        return new RecompensasDeEjecucion(0, List.of(), List.of(), 0, List.of(), List.of(), false);
    }
}
