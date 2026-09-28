package nexus.misiones.api;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import nexus.misiones.aplicacion.Cancelacion;
import nexus.misiones.aplicacion.EjecucionConMision;
import nexus.misiones.aplicacion.Historial;
import nexus.misiones.aplicacion.MisionParaJugador;
import nexus.misiones.aplicacion.PaginaDeMisiones;
import nexus.misiones.aplicacion.ParametrosDeMisiones;
import nexus.misiones.dominio.CatalogoDeMisiones;
import nexus.misiones.dominio.Ejecucion;
import nexus.misiones.dominio.EpicaDeTabla20;
import nexus.misiones.dominio.Escalon;
import nexus.misiones.dominio.EstadoDePaso;
import nexus.misiones.dominio.EstadoEjecucion;
import nexus.misiones.dominio.EstrategiaGuardada;
import nexus.misiones.dominio.MasterDeMision;
import nexus.misiones.dominio.Mision;
import nexus.misiones.dominio.Objetivo;
import nexus.misiones.dominio.PasoDeLiquidacion;
import nexus.misiones.dominio.RecompensasDeEjecucion;
import nexus.misiones.dominio.RecompensasDeMision;
import nexus.misiones.dominio.SituacionDelJugador;
import nexus.misiones.dominio.simulacion.ResultadoDeMision;
import nexus.misiones.dominio.simulacion.TiradaDeMasters;
import org.springframework.stereotype.Component;

/**
 * Traduce el dominio a las vistas del contrato (misiones.yaml), en palabras
 * del jugador. Aqui no se decide nada del juego: el estado, el motivo del
 * bloqueo o las recompensas ya vienen calculados.
 */
@Component
class VistasDeMisiones {

    /** Seccion 6.1.1, tal como la escribe el documento. */
    static final String EXPERIENCIA_POR_ENEMIGO = "10 × 1,2^(1d8) por cada enemigo derrotado";

    /** Los pasos que mueven algo del jugador; el correo no cuenta como entrega. */
    private static final Set<PasoDeLiquidacion> PASOS_DE_ENTREGA =
            EnumSet.of(PasoDeLiquidacion.LIBERACION, PasoDeLiquidacion.CREDITOS, PasoDeLiquidacion.BOTIN,
                    PasoDeLiquidacion.EPICA);

    private static final List<String> PRIORIDADES = List.of("Alta", "Media", "Baja");

    private final CatalogoDeMisiones catalogo;
    private final ParametrosDeMisiones parametros;
    private final Clock reloj;

    VistasDeMisiones(CatalogoDeMisiones catalogo, ParametrosDeMisiones parametros, Clock reloj) {
        this.catalogo = catalogo;
        this.parametros = parametros;
        this.reloj = reloj;
    }

    // ------------------------------------------------------------ misiones

    Respuestas.Pagina pagina(PaginaDeMisiones pagina) {
        return new Respuestas.Pagina(pagina.misiones().stream().map(this::resumen).toList(), pagina.total(),
                pagina.pagina(), pagina.totalPaginas(), PaginaDeMisiones.TAMANIO);
    }

    List<Respuestas.Resumen> resumenes(List<MisionParaJugador> misiones) {
        return misiones.stream().map(this::resumen).toList();
    }

    Respuestas.Resumen resumen(MisionParaJugador m) {
        Mision mision = m.mision();
        SituacionDelJugador s = m.situacion();
        return new Respuestas.Resumen(mision.id(), mision.nombre(), mision.categoria(), mision.descripcionBreve(),
                mision.imagen(), mision.dificultad(), mision.duracionHoras(), mision.nivelRecomendado(),
                mision.recompensasDestacadas(), s.estado(), s.motivoBloqueo(), s.progreso(),
                s.ultimaEjecucionId() == null ? null : s.ultimaEjecucionId().toString(), mision.destacada(),
                s.nueva(), mision.disponibleHasta(), m.favorita(), mision.origen());
    }

    /** @param intentosRestantes nulo si la mision no limita intentos */
    Respuestas.Detalle detalle(MisionParaJugador m, Integer intentosRestantes) {
        Mision mision = m.mision();
        SituacionDelJugador s = m.situacion();
        List<Respuestas.EscalonVista> escalones = new ArrayList<>();
        for (Escalon escalon : Escalon.values()) {
            Double multiplicador = parametros.multiplicadorDeEstadisticas(escalon);
            escalones.add(new Respuestas.EscalonVista(escalon,
                    multiplicador != null && s.escalonesDesbloqueados().contains(escalon), multiplicador));
        }
        Respuestas.JefeVista jefe = mision.jefe() == null ? null
                : new Respuestas.JefeVista(mision.jefe().nombre(), mision.jefe().prototipo(), mision.jefe().vida(),
                        mision.jefe().descripcion());
        return new Respuestas.Detalle(
                resumen(m),
                mision.narrativa(),
                mision.escenario(),
                new Respuestas.Objetivos(
                        mision.objetivos().stream().filter(Objetivo::principal).map(Objetivo::texto).toList(),
                        mision.objetivos().stream().filter(o -> !o.principal()).map(Objetivo::texto).toList()),
                mision.requisitosPrevios().stream()
                        .map(id -> catalogo.buscar(id).map(Mision::nombre).orElse(id))
                        .toList(),
                mision.encuentros(),
                s.escalonMasAlto(),
                escalones,
                mision.enemigos().stream()
                        .map(g -> new Respuestas.Enemigo(g.nombre(), g.cantidad(), g.descripcion()))
                        .toList(),
                jefe,
                TiradaDeMasters.probabilidadDeAlguno(mision),
                mision.masters().stream().map(VistasDeMisiones::master).toList(),
                catalogo.tabla20().stream().map(EpicaDeTabla20::comoMaster).map(VistasDeMisiones::master).toList(),
                recompensas(mision),
                new Respuestas.ExperienciaVista(EXPERIENCIA_POR_ENEMIGO,
                        parametros.recompensas().experienciaPorCompletar(mision.dificultad())),
                mision.intentos() == null || intentosRestantes == null ? null
                        : new Respuestas.IntentosVista(mision.intentos().maximo(), mision.intentos().periodo().name(),
                                intentosRestantes));
    }

    private static Respuestas.MasterVista master(MasterDeMision master) {
        return new Respuestas.MasterVista(master.nombre(), master.prototipo(), master.probabilidad(),
                new Respuestas.EpicaVista(master.epica().nombre(), master.epica().efectoGeneral(),
                        master.epica().efectoPotenciado()));
    }

    private static Respuestas.RecompensasVista recompensas(Mision mision) {
        RecompensasDeMision r = mision.recompensas();
        List<String> garantizadas = new ArrayList<>();
        if (r.creditos() > 0) {
            garantizadas.add(r.creditos() + " créditos");
        }
        for (RecompensasDeMision.ObjetoDeRecompensa objeto : r.garantizadas()) {
            garantizadas.add(objeto.cantidad() + " " + objeto.nombre()
                    + (objeto.detalle() == null ? "" : " (" + objeto.detalle() + ")"));
        }
        List<Respuestas.Potencial> potenciales = r.potenciales().stream()
                .map(p -> new Respuestas.Potencial(p.nombre(), p.probabilidad(), p.detalle()))
                .toList();
        List<Respuestas.PorObjetivo> porObjetivos = new ArrayList<>();
        for (RecompensasDeMision.BonificacionPorObjetivo bono : r.porObjetivos()) {
            if (bono.objetivo() < mision.objetivos().size()) {
                porObjetivos.add(new Respuestas.PorObjetivo(mision.objetivos().get(bono.objetivo()).texto(),
                        "+" + bono.creditos() + " créditos"));
            }
        }
        List<String> primeraVez = new ArrayList<>();
        if (r.primeraVez().creditos() > 0) {
            primeraVez.add(r.primeraVez().creditos() + " créditos adicionales");
        }
        primeraVez.addAll(r.primeraVez().otras());
        return new Respuestas.RecompensasVista(garantizadas, potenciales, porObjetivos, primeraVez);
    }

    // --------------------------------------------------------- ejecuciones

    Respuestas.MisionActiva activa(Ejecucion e, Mision mision, String penalizacion) {
        return new Respuestas.MisionActiva(e.id(), e.misionId(), mision.nombre(), mision.categoria(),
                new Respuestas.HeroeVista(e.heroe().id(), e.heroe().nombre(), e.heroe().prototipo(),
                        e.heroe().nivel()),
                e.iniciadaEn(), e.terminaEn(), e.progreso(reloj.instant()), penalizacion, e.escalon(),
                EstadoEjecucion.EN_PROGRESO.name());
    }

    Respuestas.Cancelacion cancelacion(Cancelacion c) {
        return new Respuestas.Cancelacion(c.ejecucion().id(), c.ejecucion().estado().name(), c.heroeLiberado(),
                c.penalizacion());
    }

    Respuestas.Reporte reporte(EjecucionConMision t) {
        Ejecucion e = t.ejecucion();
        ResultadoDeMision r = e.resultado();
        RecompensasDeEjecucion recompensas = e.recompensas();
        List<Respuestas.SinEntregar> sinEntregar = new ArrayList<>();
        recompensas.sinEntregar().forEach(x -> sinEntregar.add(new Respuestas.SinEntregar(x.nombre(), x.motivo())));
        sinEntregar.addAll(entregasRechazadas(e));
        boolean pendiente = e.pasosPendientes().stream().anyMatch(PASOS_DE_ENTREGA::contains);
        return new Respuestas.Reporte(
                e.id(),
                new Respuestas.MisionCorta(t.mision().id(), t.mision().nombre(), t.mision().categoria()),
                resultado(e),
                e.escalon(),
                duracion(e),
                e.terminadaEn(),
                new Respuestas.HeroeDelReporte(e.heroe().id(), e.heroe().nombre(), e.heroe().prototipo(),
                        e.heroe().nivel(), e.nivelAlcanzado()),
                new Respuestas.Combate(r.encuentrosCompletados(), r.danoInfligido(), r.danoRecibido(), r.turnos(),
                        r.habilidadesMasUsadas().stream()
                                .map(h -> new Respuestas.HabilidadUsada(h.nombre(), h.usos()))
                                .toList(),
                        r.criticos()),
                r.enemigosDerrotados().stream()
                        .map(d -> new Respuestas.EnemigoDerrotado(d.nombre(), d.cantidad()))
                        .toList(),
                r.masters().stream()
                        .filter(ResultadoDeMision.MasterEnfrentado::derrotado)
                        .map(m -> new Respuestas.MasterDerrotado(m.nombre(), m.epica().nombre()))
                        .toList(),
                r.jefeDerrotado(),
                new Respuestas.RecompensasDelReporte(recompensas.creditos(),
                        recompensas.productos().stream()
                                .map(p -> new Respuestas.Producto(
                                        p.cantidad() > 1 ? p.cantidad() + " " + p.nombre() : p.nombre(), null))
                                .toList(),
                        recompensas.epicas().stream().map(RecompensasDeEjecucion.EpicaGanada::nombre).toList(),
                        recompensas.experiencia(), sinEntregar, pendiente),
                recompensas.objetivos().stream()
                        .map(o -> new Respuestas.ObjetivoVista(o.texto(), o.cumplido(), o.bonificacion()))
                        .toList());
    }

    /**
     * Un paso de entrega que el otro servicio rechazo para siempre tambien se
     * cuenta en el reporte: nunca fallar en silencio. El motivo lleva lo que
     * contesto ese servicio.
     */
    private static List<Respuestas.SinEntregar> entregasRechazadas(Ejecucion e) {
        List<Respuestas.SinEntregar> rechazadas = new ArrayList<>();
        RecompensasDeEjecucion r = e.recompensas();
        if (e.estadoDe(PasoDeLiquidacion.CREDITOS) == EstadoDePaso.FALLIDO) {
            rechazadas.add(new Respuestas.SinEntregar(r.creditos() + " créditos",
                    "El libro de créditos rechazó el abono: " + e.motivoDe(PasoDeLiquidacion.CREDITOS)));
        }
        if (e.estadoDe(PasoDeLiquidacion.BOTIN) == EstadoDePaso.FALLIDO) {
            r.productos().forEach(p -> rechazadas.add(new Respuestas.SinEntregar(p.nombre(),
                    "El inventario rechazó la entrega: " + e.motivoDe(PasoDeLiquidacion.BOTIN))));
        }
        if (e.estadoDe(PasoDeLiquidacion.EPICA) == EstadoDePaso.FALLIDO) {
            r.epicas().stream().filter(RecompensasDeEjecucion.EpicaGanada::entregable)
                    .forEach(x -> rechazadas.add(new Respuestas.SinEntregar("Épica «" + x.nombre() + "»",
                            "El inventario rechazó la entrega: " + e.motivoDe(PasoDeLiquidacion.EPICA))));
        }
        return rechazadas;
    }

    Respuestas.Historial historial(Historial h) {
        return new Respuestas.Historial(
                h.terminadas().stream()
                        .map(t -> new Respuestas.Completada(t.ejecucion().id(), t.ejecucion().misionId(),
                                t.mision().nombre(), t.mision().categoria(), t.ejecucion().terminadaEn(),
                                resultado(t.ejecucion()), duracion(t.ejecucion())))
                        .toList(),
                h.porCategoria().stream()
                        .map(p -> new Respuestas.PorCategoria(p.categoria(), p.completadas(), p.fallidas()))
                        .toList(),
                h.mejoresTiempos().stream()
                        .map(m -> new Respuestas.MejorTiempo(m.misionId(), m.nombre(), m.duracion().toMillis()))
                        .toList(),
                h.epicas().stream()
                        .map(x -> new Respuestas.EpicaObtenida(x.nombre(), x.master(), x.obtenidaEn()))
                        .toList(),
                h.cadenas().stream()
                        .map(c -> new Respuestas.Cadena(c.nombre(), c.completadas(), c.total()))
                        .toList());
    }

    Respuestas.Estrategia estrategia(EstrategiaGuardada e) {
        List<Respuestas.RotacionVista> rotaciones = new ArrayList<>();
        for (int i = 0; i < e.rotaciones().size() && i < PRIORIDADES.size(); i++) {
            rotaciones.add(new Respuestas.RotacionVista(PRIORIDADES.get(i), e.rotaciones().get(i)));
        }
        return new Respuestas.Estrategia(e.heroeId(), e.prototipo(), e.nivel(), rotaciones, e.actualizadaEn());
    }

    private static String resultado(Ejecucion e) {
        return e.estado() == EstadoEjecucion.COMPLETADA ? "EXITO" : "FALLO";
    }

    private static long duracion(Ejecucion e) {
        return Math.max(0, Duration.between(e.iniciadaEn(), e.terminadaEn()).toMillis());
    }
}
