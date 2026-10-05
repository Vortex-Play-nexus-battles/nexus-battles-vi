package nexus.misiones.aplicacion;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import com.nexusbattles.plataforma.resiliencia.DependenciaDegradada;
import nexus.misiones.dominio.CatalogoDeMisiones;
import nexus.misiones.dominio.Ejecucion;
import nexus.misiones.dominio.EjecucionModificadaConcurrentemente;
import nexus.misiones.dominio.EpicaDeTabla20;
import nexus.misiones.dominio.EstadoEjecucion;
import nexus.misiones.dominio.EstrategiaGuardada;
import nexus.misiones.dominio.Mision;
import nexus.misiones.dominio.RepositorioDeEjecuciones;
import nexus.misiones.dominio.RepositorioDeEstrategias;
import nexus.misiones.dominio.RepositorioDeFavoritas;
import nexus.misiones.dominio.RepositorioDeEventosDeCombate;
import nexus.misiones.dominio.simulacion.AccionNoPermitida;
import nexus.misiones.dominio.simulacion.Combatiente;
import nexus.misiones.dominio.simulacion.DecisionDeTurno;
import nexus.misiones.dominio.simulacion.DetalleDeAtaque;
import nexus.misiones.dominio.simulacion.EstadisticasDeCombate;
import nexus.misiones.dominio.simulacion.EventoDeCombate;
import nexus.misiones.dominio.simulacion.Formula;
import nexus.misiones.dominio.simulacion.MotorDeCombate;
import nexus.misiones.dominio.simulacion.ResultadoDeAccion;
import nexus.misiones.dominio.simulacion.ResultadoDeTurno;
import nexus.misiones.dominio.simulacion.SimuladorDeMision;
import nexus.misiones.dominio.simulacion.Suceso;
import nexus.misiones.dominio.simulacion.TurnoParaDecidir;

/**
 * Dobles en memoria de los puertos de misiones, para probar los casos de uso
 * sin red ni Mongo. Los adaptadores de verdad tienen sus propias pruebas
 * (servidores HTTP falsos, Testcontainers y el pacto con inventario).
 */
public final class Dobles {

    private Dobles() {
    }

    /** Guarda copias, simula la version optimista y el indice unico de «una en curso por mision». */
    public static final class Ejecuciones implements RepositorioDeEjecuciones {

        private final Map<UUID, Ejecucion.Estado> guardadas = new LinkedHashMap<>();
        public RuntimeException fallarAlGuardar;
        /** Un fallo de una sola vez, solo para las ejecuciones que cumplan la condicion (un corte de luz a mitad). */
        public java.util.function.Predicate<Ejecucion> fallarUnaVezSi;
        /** Mongo no contesta la consulta de las vencidas (la de las pendientes de entrega, si). */
        public RuntimeException fallarAlConsultarVencidas;

        @Override
        public synchronized Ejecucion guardar(Ejecucion ejecucion) {
            if (fallarAlGuardar != null) {
                throw fallarAlGuardar;
            }
            if (fallarUnaVezSi != null && fallarUnaVezSi.test(ejecucion)) {
                fallarUnaVezSi = null;
                throw new IllegalStateException("se cayo Mongo al guardar");
            }
            Ejecucion.Estado anterior = guardadas.get(ejecucion.id());
            long versionActual = anterior == null ? -1 : anterior.version;
            long versionLeida = ejecucion.version() == null ? -1 : ejecucion.version();
            if (versionActual != versionLeida) {
                throw new EjecucionModificadaConcurrentemente("version vieja", null);
            }
            if (ejecucion.estado() == EstadoEjecucion.EN_PROGRESO) {
                boolean otraEnCurso = guardadas.values().stream()
                        .anyMatch(e -> !e.id.equals(ejecucion.id()) && e.jugadorUid.equals(ejecucion.jugadorUid())
                                && e.misionId.equals(ejecucion.misionId()) && e.estado == EstadoEjecucion.EN_PROGRESO);
                if (otraEnCurso) {
                    throw new EjecucionModificadaConcurrentemente("ya hay una en curso", null);
                }
            }
            Ejecucion.Estado copia = estadoDe(ejecucion);
            copia.version = versionActual + 1;
            guardadas.put(ejecucion.id(), copia);
            return Ejecucion.reconstruir(copiar(copia));
        }

        @Override
        public synchronized Optional<Ejecucion> buscar(UUID id) {
            return Optional.ofNullable(guardadas.get(id)).map(e -> Ejecucion.reconstruir(copiar(e)));
        }

        @Override
        public Optional<Ejecucion> buscarPorClave(String jugadorUid, String clave) {
            return todas().stream()
                    .filter(e -> e.jugadorUid().equals(jugadorUid) && clave.equals(e.claveIdempotencia()))
                    .findFirst();
        }

        @Override
        public List<Ejecucion> delJugador(String jugadorUid) {
            return todas().stream().filter(e -> e.jugadorUid().equals(jugadorUid))
                    .sorted(Comparator.comparing(Ejecucion::iniciadaEn).reversed()).toList();
        }

        @Override
        public List<Ejecucion> delJugadorEnMision(String jugadorUid, String misionId) {
            return delJugador(jugadorUid).stream().filter(e -> e.misionId().equals(misionId)).toList();
        }

        @Override
        public List<Ejecucion> enCursoDelJugador(String jugadorUid) {
            return delJugador(jugadorUid).stream().filter(e -> e.estado() == EstadoEjecucion.EN_PROGRESO)
                    .sorted(Comparator.comparing(Ejecucion::terminaEn)).toList();
        }

        @Override
        public long iniciadasDesde(String jugadorUid, String misionId, Instant desde) {
            return delJugadorEnMision(jugadorUid, misionId).stream()
                    .filter(e -> !e.iniciadaEn().isBefore(desde)).count();
        }

        /** Como Mongo: las listas para simular, la de plazo mas antiguo primero. */
        @Override
        public List<Ejecucion> vencidas(Instant ahora, int limite) {
            if (fallarAlConsultarVencidas != null) {
                throw fallarAlConsultarVencidas;
            }
            return todas().stream().filter(e -> e.listaParaSimular(ahora))
                    .sorted(Comparator.comparing(Ejecucion::terminaEn))
                    .limit(limite).toList();
        }

        @Override
        public List<Ejecucion> conLiquidacionPendiente(Instant ahora, int limite) {
            return todas().stream()
                    .filter(e -> e.liquidacionPendiente()
                            && (e.proximoIntento() == null || !e.proximoIntento().isAfter(ahora)))
                    .limit(limite).toList();
        }

        public List<Ejecucion> todas() {
            return guardadas.values().stream().map(e -> Ejecucion.reconstruir(copiar(e))).toList();
        }

        private static Ejecucion.Estado estadoDe(Ejecucion e) {
            Ejecucion.Estado s = new Ejecucion.Estado();
            s.id = e.id();
            s.misionId = e.misionId();
            s.jugadorUid = e.jugadorUid();
            s.heroe = e.heroe();
            s.estrategia = e.estrategia();
            s.escalon = e.escalon();
            s.iniciadaEn = e.iniciadaEn();
            s.terminaEn = e.terminaEn();
            s.semilla = e.semilla();
            s.claveIdempotencia = e.claveIdempotencia();
            s.estado = e.estado();
            s.terminadaEn = e.terminadaEn();
            s.resultado = e.resultado();
            s.recompensas = e.recompensas();
            s.pasos = new HashMap<>(e.pasos());
            s.motivos = new HashMap<>(e.motivos());
            s.intentosDeLiquidacion = e.intentosDeLiquidacion();
            s.proximoIntento = e.proximoIntento();
            s.ultimoError = e.ultimoError();
            s.nivelAlcanzado = e.nivelAlcanzado();
            s.experienciaAcumulada = e.experienciaAcumulada();
            s.intentosDeSimulacion = e.intentosDeSimulacion();
            s.version = e.version();
            return s;
        }

        private static Ejecucion.Estado copiar(Ejecucion.Estado e) {
            return estadoDe(Ejecucion.reconstruir(e));
        }
    }

    public static final class Estrategias implements RepositorioDeEstrategias {
        public final Map<String, EstrategiaGuardada> guardadas = new HashMap<>();

        @Override
        public Optional<EstrategiaGuardada> buscar(String jugadorUid, String heroeId) {
            return Optional.ofNullable(guardadas.get(jugadorUid + ":" + heroeId));
        }

        @Override
        public EstrategiaGuardada guardar(EstrategiaGuardada estrategia) {
            guardadas.put(estrategia.jugadorUid() + ":" + estrategia.heroeId(), estrategia);
            return estrategia;
        }
    }

    public static final class Favoritas implements RepositorioDeFavoritas {
        public final Map<String, Set<String>> porJugador = new HashMap<>();

        @Override
        public Set<String> delJugador(String jugadorUid) {
            return Set.copyOf(porJugador.getOrDefault(jugadorUid, Set.of()));
        }

        @Override
        public void marcar(String jugadorUid, String misionId, Instant ahora) {
            porJugador.computeIfAbsent(jugadorUid, k -> new HashSet<>()).add(misionId);
        }

        @Override
        public void desmarcar(String jugadorUid, String misionId) {
            porJugador.getOrDefault(jugadorUid, new HashSet<>()).remove(misionId);
        }
    }

    public static final class Catalogo implements CatalogoDeMisiones {
        private final List<Mision> misiones;
        private final List<EpicaDeTabla20> tabla20;

        public Catalogo(List<Mision> misiones, List<EpicaDeTabla20> tabla20) {
            this.misiones = misiones;
            this.tabla20 = tabla20;
        }

        @Override
        public List<Mision> todas() {
            return misiones;
        }

        @Override
        public Optional<Mision> buscar(String id) {
            return misiones.stream().filter(m -> m.id().equals(id)).findFirst();
        }

        @Override
        public List<EpicaDeTabla20> tabla20() {
            return tabla20;
        }
    }

    /** El inventario: heroes con dueno, disponibilidad, equipo y estadisticas; recuerda lo que se le pide. */
    public static final class Inventario implements InventarioDeHeroes {
        public final Map<String, HeroeDelInventario> heroes = new HashMap<>();
        /** Lo que publica {@code .../estadisticas}; por defecto las de siempre, sin formulas. */
        public EstadisticasDelHeroe estadisticasDelHeroe = new EstadisticasDelHeroe(8, 44, 11);
        public RuntimeException fallarAlPedirEstadisticas;
        public EquipoDelHeroe equipoDelHeroe = new EquipoDelHeroe(List.of(), List.of());
        public RuntimeException fallarAlPedirEquipo;
        public final Set<String> equipados = new HashSet<>();
        public final Map<String, UUID> bloqueados = new HashMap<>();
        public final List<String> llamadas = new ArrayList<>();
        public final List<List<ProductoAEntregar>> entregas = new ArrayList<>();
        public final List<String> clavesDeEntrega = new ArrayList<>();
        public final Map<String, Double> experienciaSumada = new HashMap<>();
        public RuntimeException fallarAlLiberar;
        public RuntimeException fallarAlBloquear;
        public RuntimeException fallarAlEntregar;
        /** Falla solo las entregas cuya clave cumpla la condicion (el botin sin la epica, o al reves). */
        public java.util.function.Predicate<String> fallarEntregaSi;

        public Inventario conHeroe(String id, String duenoUid, String productoId, boolean equipado) {
            return conHeroeEnNivel(id, duenoUid, productoId, equipado, 1);
        }

        public Inventario conHeroeEnNivel(String id, String duenoUid, String productoId, boolean equipado,
                                          int nivel) {
            heroes.put(id, new HeroeDelInventario(id, productoId, duenoUid, "HEROE", "Vorn", true, null, null, nivel,
                    0.0));
            if (equipado) {
                equipados.add(id);
            }
            return this;
        }

        @Override
        public HeroeDelInventario consultar(String jugadorUid, String heroeId) {
            llamadas.add("consultar " + heroeId);
            HeroeDelInventario heroe = heroes.get(heroeId);
            if (heroe == null) {
                throw new HeroeNoEncontrado();
            }
            UUID bloqueo = bloqueados.get(heroeId);
            if (bloqueo != null) {
                return new HeroeDelInventario(heroe.id(), heroe.productoId(), heroe.propietarioUid(), heroe.tipo(),
                        heroe.nombrePropio(), false, null, bloqueo.toString(), heroe.nivel(), heroe.experiencia());
            }
            return heroe;
        }

        @Override
        public boolean equipado(String jugadorUid, String heroeId) {
            return equipados.contains(heroeId);
        }

        @Override
        public EstadisticasDelHeroe estadisticas(String jugadorUid, String heroeId) {
            if (fallarAlPedirEstadisticas != null) {
                throw fallarAlPedirEstadisticas;
            }
            return estadisticasDelHeroe;
        }

        @Override
        public EquipoDelHeroe equipo(String jugadorUid, String heroeId) {
            if (fallarAlPedirEquipo != null) {
                throw fallarAlPedirEquipo;
            }
            return equipoDelHeroe;
        }

        @Override
        public void bloquear(String heroeId, String jugadorUid, UUID ejecucionId) {
            llamadas.add("bloquear " + heroeId);
            if (fallarAlBloquear != null) {
                throw fallarAlBloquear;
            }
            if (bloqueados.containsKey(heroeId) && !bloqueados.get(heroeId).equals(ejecucionId)) {
                throw HeroeOcupado.enMision();
            }
            bloqueados.put(heroeId, ejecucionId);
        }

        @Override
        public ProgresionDelHeroe liberar(String heroeId, String jugadorUid, UUID ejecucionId, double experiencia) {
            llamadas.add("liberar " + heroeId + " " + experiencia);
            if (fallarAlLiberar != null) {
                throw fallarAlLiberar;
            }
            if (ejecucionId.equals(bloqueados.get(heroeId))) {
                bloqueados.remove(heroeId);
                experienciaSumada.merge(heroeId, experiencia, Double::sum);
            }
            double total = experienciaSumada.getOrDefault(heroeId, 0.0);
            return new ProgresionDelHeroe(total >= 100 ? 2 : 1, total >= 100 ? total - 100 : total);
        }

        @Override
        public void entregar(String jugadorUid, UUID ejecucionId, List<ProductoAEntregar> productos, String clave) {
            llamadas.add("entregar " + clave);
            if (fallarAlEntregar != null) {
                throw fallarAlEntregar;
            }
            if (fallarEntregaSi != null && fallarEntregaSi.test(clave)) {
                throw caido("inventario");
            }
            if (!clavesDeEntrega.contains(clave)) {
                clavesDeEntrega.add(clave);
                entregas.add(productos);
            }
        }
    }

    public static final class Productos implements CatalogoDeProductos {
        public final Map<String, String> prototipos = new HashMap<>();
        /** Nombre de los productos que no son heroes: armas, armaduras, items, epicas. */
        public final Map<String, String> nombres = new HashMap<>();
        public RuntimeException fallarAlPedirNombre;
        public final List<String> consultadas = new ArrayList<>();

        @Override
        public String prototipoDe(String productoId) {
            if (!prototipos.containsKey(productoId)) {
                throw new HeroeNoEncontrado();
            }
            return prototipos.get(productoId);
        }

        @Override
        public String nombreDe(String productoId) {
            consultadas.add(productoId);
            if (fallarAlPedirNombre != null) {
                throw fallarAlPedirNombre;
            }
            return nombres.get(productoId);
        }
    }

    /** Heroes: valida todo salvo lo que se le diga; decide ataque basico; 10 x 1,2^dado; estadisticas fijas. */
    public static final class Heroes implements ServicioDeHeroes {
        public String motivoDeRechazo;
        /** Con un motivo de rechazo, rechaza solo las estrategias con rotaciones escritas; la vacia (la heuristica) pasa. */
        public boolean rechazarSoloLasEscritas;
        /** Si no es nulo, lo que heroes devuelve como rotaciones con el nombre exacto de la Tabla 7. */
        public List<List<String>> rotacionesCanonicas;
        public final Set<String> sanadores = Set.of("Chamán", "Médico");
        public final List<String> validaciones = new ArrayList<>();
        public int vidaDeLosEnemigos = 5;
        public RuntimeException fallarAlDecidir;
        /** Lo que dice heroes que sabe hacer un enemigo en su nivel, con «Ataque básico» al final. */
        public List<String> habilidadesValidas = List.of("Ataque básico");
        public Formula ataqueDeNivel = new Formula(10, 1, 6);
        public Formula danoDeNivel = new Formula(1, 1, 4);
        public final List<TurnoParaDecidir> decisiones = new ArrayList<>();
        public RuntimeException fallarAlValidar;

        @Override
        public VeredictoDeEstrategia validarEstrategia(String prototipo, int nivel, List<List<String>> rotaciones) {
            validaciones.add(prototipo + "@" + nivel + " " + rotaciones);
            if (fallarAlValidar != null) {
                throw fallarAlValidar;
            }
            if (motivoDeRechazo != null && !(rechazarSoloLasEscritas && rotaciones.isEmpty())) {
                return new VeredictoDeEstrategia(false, motivoDeRechazo, null, habilidadesValidas);
            }
            return new VeredictoDeEstrategia(true, null, rotacionesCanonicas != null ? rotacionesCanonicas : rotaciones,
                    habilidadesValidas);
        }

        @Override
        public VeredictoDeComposicion validarIndividual(String prototipo) {
            return sanadores.contains(prototipo)
                    ? new VeredictoDeComposicion(false, "Un sanador solo participa en combate por equipos.")
                    : new VeredictoDeComposicion(true, null);
        }

        /** Cada vista por nivel que se pidio, como «prototipo@nivel» (D-42: el nivel de los enemigos). */
        public final List<String> nivelesPedidos = new ArrayList<>();

        @Override
        public EstadisticasDeNivel enNivel(String prototipo, int nivel) {
            nivelesPedidos.add(prototipo + "@" + nivel);
            return new EstadisticasDeNivel(10, vidaDeLosEnemigos, 5, ataqueDeNivel, danoDeNivel, null);
        }

        @Override
        public DecisionDeTurno decidir(TurnoParaDecidir turno) {
            decisiones.add(turno);
            if (fallarAlDecidir != null) {
                throw fallarAlDecidir;
            }
            return new DecisionDeTurno(DecisionDeTurno.ATAQUE_BASICO, 0, turno.cursores());
        }

        @Override
        public double porEnemigoDerrotado(int dado) {
            return 10 * Math.pow(1.2, dado);
        }
    }

    /**
     * El motor de combate, en memoria y sin azar: el heroe pega {@link #danoDelHeroe}
     * y los enemigos {@link #danoDeLosEnemigos}, salvo que se diga otra cosa.
     * Quien pega se sabe por el id del combatiente ({@link #HEROE} o
     * {@link #RIVAL}), no por sus numeros. Recuerda cada llamada para que las
     * pruebas puedan decir QUE se le pidio al motor, y no solo que salio.
     *
     * <p>Reglas minimas del motor real que las pruebas necesitan: el poder se
     * recupera dos por turno, una accion especial gasta su costo, sin poder
     * suficiente se juega el ataque basico en valor base, y las acciones de
     * {@link #rechazadas} se rechazan con 409.
     */
    public static final class Motor implements MotorDeCombate {
        public static final String HEROE = SimuladorDeMision.ID_DEL_HEROE;
        public static final String RIVAL = SimuladorDeMision.ID_DEL_RIVAL;
        /** 6.1.2: «tienen dos turnos de recarga»: tras jugarla no se puede otra vez hasta pasados dos turnos propios. */
        static final int TURNOS_DE_RECARGA_DE_UNA_EPICA = 2;

        public int danoDelHeroe = 50;
        public int danoDeLosEnemigos = 1;
        public RuntimeException fallar;
        /** Lo que cuesta cada accion especial; las que no estan cuestan cero. */
        public final Map<String, Integer> costos = new HashMap<>();
        /** Acciones que el motor rechaza, con su motivo. */
        public final Map<String, String> rechazadas = new HashMap<>();
        /** Quien cae al empezar su turno (un sangrado), o nulo. */
        public String caeAlIniciar;
        /** Quien tira critico siempre. */
        public String criticoPara;
        /** Prototipos de los que el motor dice que solo sanan (sin ataque basico). */
        public final Set<String> sanadores = new HashSet<>();
        public final List<String> llamadas = new ArrayList<>();
        public final List<Long> semillas = new ArrayList<>();
        public final List<List<Combatiente>> recibidos = new ArrayList<>();
        public final List<String> accionesPedidas = new ArrayList<>();

        @Override
        public ResultadoDeTurno iniciarTurno(String combatiente, List<Combatiente> combatientes, Long semilla) {
            llamadas.add("turnos " + combatiente);
            semillas.add(semilla);
            recibidos.add(combatientes);
            if (fallar != null) {
                throw fallar;
            }
            List<Combatiente> nuevos = new ArrayList<>();
            List<Suceso> sucesos = new ArrayList<>();
            for (Combatiente original : combatientes) {
                Combatiente c = resuelto(original);
                if (c.id().equals(combatiente)) {
                    if (c.id().equals(caeAlIniciar)) {
                        sucesos.add(new Suceso("DANO_POR_TURNO", c.id(), RIVAL, "Sangrado", c.vidaActual()));
                        c = con(c, 0, c.poderActual(), c.turnosJugados());
                    } else {
                        int maximo = c.estadisticas().poder();
                        int poder = Math.min(maximo, c.poderActual() + 2);
                        if (poder > c.poderActual()) {
                            sucesos.add(new Suceso("PODER_RECUPERADO", c.id(), null, null, poder - c.poderActual()));
                        }
                        c = con(c, c.vidaActual(), poder, c.turnosJugados());
                    }
                    c = conRecargas(c, unTurnoMenos(c.recargas()));
                }
                nuevos.add(c);
            }
            return new ResultadoDeTurno(combatiente, sucesos, nuevos);
        }

        @Override
        public ResultadoDeAccion resolverAccion(String accion, String ejecutor, String objetivo,
                                                List<Combatiente> combatientes, Long semilla) {
            llamadas.add("acciones " + ejecutor + " " + accion);
            accionesPedidas.add(accion);
            semillas.add(semilla);
            recibidos.add(combatientes);
            if (fallar != null) {
                throw fallar;
            }
            if (rechazadas.containsKey(accion)) {
                throw new AccionNoPermitida(rechazadas.get(accion), accion + " no se puede jugar ahora.");
            }
            Combatiente actor = resuelto(combatientes.stream().filter(c -> c.id().equals(ejecutor)).findFirst()
                    .orElseThrow());
            Combatiente blanco = resuelto(combatientes.stream().filter(c -> !c.id().equals(ejecutor)).findFirst()
                    .orElseThrow());
            boolean sana = sanadores.contains(actor.prototipo());
            if (sana && ATAQUE_BASICO.equals(accion)) {
                throw new AccionNoPermitida("SANADOR_NO_ATACA", "Un sanador no ataca.");
            }
            boolean basica = ATAQUE_BASICO.equals(accion) || SANACION_BASICA.equals(accion);
            int costo = basica ? 0 : costos.getOrDefault(accion, 0);
            boolean enValorBase = costo > actor.poderActual();
            String ejecutada = enValorBase ? (sana ? SANACION_BASICA : ATAQUE_BASICO) : accion;
            int gasto = enValorBase ? 0 : costo;
            int dano = sana ? 0 : ejecutor.equals(HEROE) ? danoDelHeroe : danoDeLosEnemigos;
            boolean critico = ejecutor.equals(criticoPara);
            Combatiente blancoDespues = con(blanco, Math.max(0, blanco.vidaActual() - dano), blanco.poderActual(),
                    blanco.turnosJugados());
            Combatiente actorDespues = con(actor, actor.vidaActual(), actor.poderActual() - gasto,
                    actor.turnosJugados() + 1);
            if (actor.epicas().contains(accion)) {
                Map<String, Integer> recargasNuevas = new HashMap<>(actorDespues.recargas());
                recargasNuevas.put(accion, TURNOS_DE_RECARGA_DE_UNA_EPICA);
                actorDespues = conRecargas(actorDespues, recargasNuevas);
            }
            List<Combatiente> nuevos = new ArrayList<>();
            for (Combatiente c : combatientes) {
                nuevos.add(c.id().equals(ejecutor) ? actorDespues : blancoDespues);
            }
            DetalleDeAtaque golpe = sana ? null : new DetalleDeAtaque(15, blanco.estadisticas().defensa(), dano > 0,
                    critico ? DetalleDeAtaque.CRITICO : dano > 0 ? "CAUSAR_DANO" : "SIN_EFECTO", 4000, 100, dano,
                    dano);
            List<Suceso> sucesos = new ArrayList<>();
            if (dano > 0) {
                sucesos.add(new Suceso("DANO", blanco.id(), ejecutor, ejecutada, dano));
            }
            return new ResultadoDeAccion(accion, ejecutada, enValorBase, ejecutor, blanco.id(), golpe, sucesos,
                    nuevos);
        }

        /** El combatiente como lo devuelve el motor: con estadisticas, poder y acciones resueltos. */
        private Combatiente resuelto(Combatiente c) {
            EstadisticasDeCombate e = c.estadisticas() != null ? c.estadisticas()
                    : new EstadisticasDeCombate(10, 44, 11, new Formula(10, 1, 6), new Formula(1, 1, 4), null);
            Integer poder = c.poderActual() == null ? Integer.valueOf(e.poder()) : c.poderActual();
            List<String> acciones = sanadores.contains(c.prototipo()) ? List.of(SANACION_BASICA)
                    : List.of(ATAQUE_BASICO);
            return new Combatiente(c.id(), c.prototipo(), c.nivel(), e, Math.min(c.vidaActual(), e.vida()), poder,
                    c.turnosJugados(), c.cargas(), c.efectos(), c.equipamiento(), c.epicas(), c.ultimoDanoRecibido(),
                    c.recargas(), acciones);
        }

        private static Map<String, Integer> unTurnoMenos(Map<String, Integer> recargas) {
            Map<String, Integer> nuevas = new HashMap<>();
            recargas.forEach((accion, faltan) -> {
                if (faltan > 1) {
                    nuevas.put(accion, faltan - 1);
                }
            });
            return nuevas;
        }

        private static Combatiente conRecargas(Combatiente c, Map<String, Integer> recargas) {
            return new Combatiente(c.id(), c.prototipo(), c.nivel(), c.estadisticas(), c.vidaActual(), c.poderActual(),
                    c.turnosJugados(), c.cargas(), c.efectos(), c.equipamiento(), c.epicas(), c.ultimoDanoRecibido(),
                    recargas, c.acciones());
        }

        private static Combatiente con(Combatiente c, int vida, Integer poder, int turnos) {
            return new Combatiente(c.id(), c.prototipo(), c.nivel(), c.estadisticas(), vida, poder, turnos,
                    c.cargas(), c.efectos(), c.equipamiento(), c.epicas(), c.ultimoDanoRecibido(), c.recargas(),
                    c.acciones());
        }
    }

    /** Los turnos de combate guardados, en memoria. */
    public static final class Eventos implements RepositorioDeEventosDeCombate {
        public final Map<UUID, List<EventoDeCombate>> porEjecucion = new LinkedHashMap<>();
        public int escrituras;
        public RuntimeException fallarAlGuardar;

        @Override
        public void reemplazar(UUID ejecucionId, List<EventoDeCombate> eventos) {
            if (fallarAlGuardar != null) {
                throw fallarAlGuardar;
            }
            escrituras++;
            porEjecucion.put(ejecucionId, List.copyOf(eventos));
        }

        @Override
        public List<EventoDeCombate> de(UUID ejecucionId) {
            return porEjecucion.getOrDefault(ejecucionId, List.of());
        }
    }

    public static final class Libro implements LibroDeCreditos {
        public final Map<String, Integer> acreditado = new LinkedHashMap<>();
        /** Cada vez que se le pidio acreditar, con o sin exito: la referencia que se uso. */
        public final List<String> intentos = new ArrayList<>();
        public RuntimeException fallar;

        @Override
        public void acreditar(String jugadorUid, int monto, String refId, String concepto) {
            intentos.add(refId);
            if (fallar != null) {
                throw fallar;
            }
            acreditado.putIfAbsent(refId, monto);
        }
    }

    public static final class Directorio implements DirectorioDeJugadores {
        public final Map<String, Contacto> contactos = new HashMap<>();

        @Override
        public Optional<Contacto> contacto(String jugadorUid) {
            return Optional.ofNullable(contactos.get(jugadorUid));
        }
    }

    public static final class Correo implements CorreoDeMisiones {
        public final Map<String, String> enviados = new LinkedHashMap<>();
        /** Cada vez que se le pidio enviar, con o sin exito: la clave de idempotencia que se uso. */
        public final List<String> intentos = new ArrayList<>();
        public final AtomicReference<RuntimeException> fallar = new AtomicReference<>();
        /** Falla solo los correos cuya clave cumpla la condicion (el de la epica sin el de fin, o al reves). */
        public java.util.function.Predicate<String> fallarClaveSi;

        @Override
        public void enviar(DirectorioDeJugadores.Contacto contacto, String asunto, String mensaje, String clave) {
            intentos.add(clave);
            if (fallar.get() != null) {
                throw fallar.get();
            }
            if (fallarClaveSi != null && fallarClaveSi.test(clave)) {
                throw caido("correo");
            }
            enviados.putIfAbsent(clave, asunto + " | " + mensaje);
        }
    }

    /**
     * La bandeja del jugador: guarda el aviso por su id y, como el servicio de
     * verdad, un id repetido no crea un segundo aviso (alli es un 409 que el
     * cliente da por entregado). {@code intentos} cuenta tambien los repetidos.
     */
    public static final class Avisos implements AvisosDeMisiones {
        public final Map<String, String> enBandeja = new LinkedHashMap<>();
        public final Map<String, String> destinatarios = new LinkedHashMap<>();
        public final Map<String, Instant> creadas = new LinkedHashMap<>();
        public final List<String> intentos = new ArrayList<>();
        public final AtomicReference<RuntimeException> fallar = new AtomicReference<>();
        /** Si no es nulo, falla solo a partir de este numero de avisos ya dados en la vuelta. */
        public Integer fallarDespuesDe;
        /** Falla solo los avisos cuyo id cumpla la condicion (el de la epica sin el de fin, o al reves). */
        public java.util.function.Predicate<String> fallarIdSi;

        @Override
        public void avisar(String jugadorUid, String id, String titulo, String cuerpo, Instant creadaEn) {
            intentos.add(id);
            if (fallarIdSi != null && fallarIdSi.test(id)) {
                throw caido("notificaciones");
            }
            if (fallar.get() != null && (fallarDespuesDe == null || enBandeja.size() >= fallarDespuesDe)) {
                throw fallar.get();
            }
            if (enBandeja.putIfAbsent(id, titulo + " | " + cuerpo) == null) {
                destinatarios.put(id, jugadorUid);
                creadas.put(id, creadaEn);
            }
        }
    }

    public static DependenciaDegradada caido(String dependencia) {
        return new DependenciaDegradada(dependencia, dependencia, new RuntimeException("sin respuesta"));
    }
}
