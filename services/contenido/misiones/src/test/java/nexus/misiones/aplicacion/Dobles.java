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
import nexus.misiones.dominio.simulacion.DecisionDeTurno;
import nexus.misiones.dominio.simulacion.Golpe;
import nexus.misiones.dominio.simulacion.ResolutorDeGolpes;
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

        @Override
        public Ejecucion guardar(Ejecucion ejecucion) {
            if (fallarAlGuardar != null) {
                throw fallarAlGuardar;
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
        public Optional<Ejecucion> buscar(UUID id) {
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

        @Override
        public List<Ejecucion> vencidas(Instant ahora, int limite) {
            return todas().stream().filter(e -> e.estado() == EstadoEjecucion.EN_PROGRESO && e.vencida(ahora))
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
        public final Set<String> equipados = new HashSet<>();
        public final Map<String, UUID> bloqueados = new HashMap<>();
        public final List<String> llamadas = new ArrayList<>();
        public final List<List<ProductoAEntregar>> entregas = new ArrayList<>();
        public final List<String> clavesDeEntrega = new ArrayList<>();
        public final Map<String, Double> experienciaSumada = new HashMap<>();
        public RuntimeException fallarAlLiberar;
        public RuntimeException fallarAlBloquear;
        public RuntimeException fallarAlEntregar;

        public Inventario conHeroe(String id, String duenoUid, String productoId, boolean equipado) {
            heroes.put(id, new HeroeDelInventario(id, productoId, duenoUid, "HEROE", "Vorn", true, null, null, 1, 0.0));
            if (equipado) {
                equipados.add(id);
            }
            return this;
        }

        @Override
        public HeroeDelInventario consultar(String heroeId) {
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
            return new EstadisticasDelHeroe(8, 44, 11);
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
            if (!clavesDeEntrega.contains(clave)) {
                clavesDeEntrega.add(clave);
                entregas.add(productos);
            }
        }
    }

    public static final class Productos implements CatalogoDeProductos {
        public final Map<String, String> prototipos = new HashMap<>();

        @Override
        public String prototipoDe(String productoId) {
            if (!prototipos.containsKey(productoId)) {
                throw new HeroeNoEncontrado();
            }
            return prototipos.get(productoId);
        }
    }

    /** Heroes: valida todo salvo lo que se le diga; decide ataque basico; 10 x 1,2^dado; estadisticas fijas. */
    public static final class Heroes implements ServicioDeHeroes {
        public String motivoDeRechazo;
        public final Set<String> sanadores = Set.of("Chamán", "Médico");
        public final List<String> validaciones = new ArrayList<>();
        public int vidaDeLosEnemigos = 5;
        public RuntimeException fallarAlDecidir;

        @Override
        public VeredictoDeEstrategia validarEstrategia(String prototipo, int nivel, List<List<String>> rotaciones) {
            validaciones.add(prototipo + "@" + nivel + " " + rotaciones);
            if (motivoDeRechazo != null) {
                return new VeredictoDeEstrategia(false, motivoDeRechazo, null, List.of("Ataque básico"));
            }
            return new VeredictoDeEstrategia(true, null, rotaciones, List.of("Ataque básico"));
        }

        @Override
        public VeredictoDeComposicion validarIndividual(String prototipo) {
            return sanadores.contains(prototipo)
                    ? new VeredictoDeComposicion(false, "Un sanador solo participa en combate por equipos.")
                    : new VeredictoDeComposicion(true, null);
        }

        @Override
        public EstadisticasDeNivel enNivel(String prototipo, int nivel) {
            return new EstadisticasDeNivel(10, vidaDeLosEnemigos, 5);
        }

        @Override
        public DecisionDeTurno decidir(TurnoParaDecidir turno) {
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
     * El motor: el heroe pega fuerte y los enemigos poco, salvo que se diga
     * otra cosa. Distingue quien pega por la defensa del objetivo: la del
     * heroe (11, la de {@link Inventario#estadisticas}) solo la tiene el heroe.
     */
    public static final class Motor implements ResolutorDeGolpes {
        public int danoDelHeroe = 50;
        public int danoDeLosEnemigos = 1;
        public int defensaDelHeroe = 11;
        public RuntimeException fallar;

        @Override
        public Golpe resolver(String prototipoAtacante, int defensaObjetivo, Long semilla) {
            if (fallar != null) {
                throw fallar;
            }
            return new Golpe(defensaObjetivo == defensaDelHeroe ? danoDeLosEnemigos : danoDelHeroe, false);
        }
    }

    public static final class Libro implements LibroDeCreditos {
        public final Map<String, Integer> acreditado = new LinkedHashMap<>();
        public RuntimeException fallar;

        @Override
        public void acreditar(String jugadorUid, int monto, String refId, String concepto) {
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
        public final AtomicReference<RuntimeException> fallar = new AtomicReference<>();

        @Override
        public void enviar(DirectorioDeJugadores.Contacto contacto, String asunto, String mensaje, String clave) {
            if (fallar.get() != null) {
                throw fallar.get();
            }
            enviados.putIfAbsent(clave, asunto + " | " + mensaje);
        }
    }

    public static DependenciaDegradada caido(String dependencia) {
        return new DependenciaDegradada(dependencia, dependencia, new RuntimeException("sin respuesta"));
    }
}
