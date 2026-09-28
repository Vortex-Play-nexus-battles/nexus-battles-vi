package com.nexusbattles.plataforma.torneos.torneo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.nexusbattles.plataforma.torneos.torneo.TorneoRechazado.Motivo;

/**
 * Casos de uso de M13: crear y cancelar un torneo, registrar equipos,
 * inscribirlos pagando, iniciar (rellenar con la maquina y generar el arbol),
 * registrar resultados y premiar al campeon.
 *
 * <p><b>Ninguna llamada HTTP dentro de una transaccion (B10).</b> Lo que
 * consulta a otro servicio antes de decidir (lista negra, sanciones, la
 * reserva de la inscripcion) se hace antes de abrir la transaccion; lo que hay
 * que hacerle a otro servicio despues de decidir (cobrar, devolver, premiar,
 * avisar) se guarda como {@link Operacion} dentro de la transaccion y lo
 * ejecuta {@link ProcesadorDeOperaciones} cuando ya se confirmo. Por eso los
 * metodos que escriben no son {@code @Transactional}: abren sus propias
 * transacciones cortas con {@link TransactionTemplate}.
 *
 * <p><b>Un torneo a la vez.</b> Toda transaccion que cambia un torneo o sus
 * equipos empieza bloqueando la fila del torneo
 * ({@link TorneoRepository#bloquear}). Dos inicios simultaneos, dos
 * inscripciones por el ultimo cupo o dos resultados a la vez se ordenan en vez
 * de pisarse.
 */
@Service
public class TorneosService {

    private static final Logger BITACORA = LoggerFactory.getLogger(TorneosService.class);

    private final TorneoRepository torneos;
    private final EquipoRepository equipos;
    private final EncuentroRepository encuentros;
    private final OperacionRepository operaciones;
    private final LibroDeCreditos libro;
    private final FiltroDeNombres filtro;
    private final ConsultaDeSanciones sanciones;
    private final ProcesadorDeOperaciones procesador;
    private final Hitos hitos;
    private final PoliticaDePremio premios;
    private final TransactionTemplate transaccion;
    private final Clock reloj;

    public TorneosService(TorneoRepository torneos, EquipoRepository equipos, EncuentroRepository encuentros,
                          OperacionRepository operaciones, LibroDeCreditos libro, FiltroDeNombres filtro,
                          ConsultaDeSanciones sanciones, ProcesadorDeOperaciones procesador, Hitos hitos,
                          PoliticaDePremio premios, TransactionTemplate transaccion, Clock reloj) {
        this.torneos = torneos;
        this.equipos = equipos;
        this.encuentros = encuentros;
        this.operaciones = operaciones;
        this.libro = libro;
        this.filtro = filtro;
        this.sanciones = sanciones;
        this.procesador = procesador;
        this.hitos = hitos;
        this.premios = premios;
        this.transaccion = transaccion;
        this.reloj = reloj;
    }

    /** Torneo con todo lo que la vista necesita. */
    public record TorneoCompleto(Torneo torneo, List<Equipo> equipos, List<Encuentro> encuentros,
                                 List<Operacion> operaciones, PoliticaDePremio.Premio premio) {
        public long inscritos() {
            return equipos.stream().filter(Equipo::inscrito).count();
        }
    }

    public record SolicitudDeTorneo(String nombre, OffsetDateTime inscripcionesCierranEn, Integer costoInscripcion) { }

    public record SolicitudDeEquipo(String nombre, String avatar, UUID companeroUid) { }

    /**
     * {@code ganadorEquipoId} lo manda el administrador; {@code ganadorUid}
     * (contrato 1.1.0) lo manda salas-partidas, que solo conoce al jugador en
     * pie. Si vienen los dos manda el equipo.
     */
    public record SolicitudDeResultado(UUID ganadorEquipoId, UUID ganadorUid, UUID partidaId, String motivo) {
        public SolicitudDeResultado(UUID ganadorEquipoId, UUID partidaId, String motivo) {
            this(ganadorEquipoId, null, partidaId, motivo);
        }
    }

    // ---------------------------------------------------------------- consultas

    @Transactional(readOnly = true)
    public List<TorneoCompleto> listar() {
        return torneos.findAllByOrderByCreadoEnDesc().stream()
                .map(t -> new TorneoCompleto(t, equipos.findByTorneoIdOrderByCreadoEnAsc(t.id()),
                        encuentros.findByTorneoIdOrderByNumeroAsc(t.id()), List.of(), t.premio(premios)))
                .toList();
    }

    @Transactional(readOnly = true)
    public TorneoCompleto obtener(UUID torneoId) {
        return completar(torneoDe(torneoId));
    }

    // ---------------------------------------------------------------- RF-TOR-001

    public TorneoCompleto crear(Actor actor, SolicitudDeTorneo solicitud) {
        exigirAdministrador(actor, "solo un administrador crea torneos");
        if (solicitud.nombre() == null || solicitud.nombre().strip().length() < 3
                || solicitud.inscripcionesCierranEn() == null) {
            throw new TorneoRechazado(Motivo.SOLICITUD_INVALIDA, "hace falta el nombre (3 caracteres o mas) y la fecha de cierre");
        }
        int costo = solicitud.costoInscripcion() == null ? 0 : solicitud.costoInscripcion();
        if (costo < 0) {
            throw new TorneoRechazado(Motivo.SOLICITUD_INVALIDA, "el costo de inscripcion no puede ser negativo");
        }
        return transaccion.execute(estado -> {
            // Sin este cerrojo dos administradores a la vez pasaban los dos la
            // comprobacion de la ventana y quedaban dos torneos en 91 dias.
            torneos.cerrojoDeLaVentana();
            OffsetDateTime ahora = ahora();
            torneos.findFirstByEstadoNotAndCreadoEnAfterOrderByCreadoEnDesc(Torneo.Estado.CANCELADO,
                            ahora.minusDays(Torneo.DIAS_ENTRE_TORNEOS))
                    .ifPresent(reciente -> {
                        OffsetDateTime proxima = reciente.creadoEn().plusDays(Torneo.DIAS_ENTRE_TORNEOS);
                        throw new TorneoRechazado(Motivo.VENTANA_DE_91_DIAS,
                                "ya hay un torneo («" + reciente.nombre() + "») dentro de los ultimos "
                                        + Torneo.DIAS_ENTRE_TORNEOS + " dias; el siguiente se puede crear desde " + proxima,
                                proxima);
                    });
            Torneo torneo = new Torneo(UUID.randomUUID(), solicitud.nombre().strip(), actor.id(), ahora,
                    solicitud.inscripcionesCierranEn(), costo, premios.premio());
            torneos.save(torneo);
            BITACORA.info("Torneo creado: id={} nombre={} por={} costo={} premio={}", torneo.id(), torneo.nombre(),
                    actor.id(), costo, premios.premio());
            return completar(torneo);
        });
    }

    /**
     * Cancela antes del inicio (CA-04 de RF-TOR-001). El cambio a CANCELADO y
     * la devolucion de cada inscripcion pagada se guardan juntos; las
     * devoluciones se ejecutan despues, fuera de la transaccion, y lo que el
     * libro no acepte ahora lo reintenta la tarea programada.
     */
    public TorneoCompleto cancelar(Actor actor, UUID torneoId, String motivo) {
        exigirAdministrador(actor, "solo un administrador cancela torneos");
        if (motivo == null || motivo.isBlank()) {
            throw new TorneoRechazado(Motivo.SOLICITUD_INVALIDA, "la cancelacion lleva su motivo");
        }
        transaccion.executeWithoutResult(estado -> {
            Torneo torneo = bloquear(torneoId);
            if (!torneo.admiteInscripciones()) {
                throw new TorneoRechazado(Motivo.ESTADO_NO_PERMITE, "solo se cancela un torneo antes de que empiece");
            }
            OffsetDateTime ahora = ahora();
            List<Operacion> nuevas = new ArrayList<>();
            for (Equipo equipo : equipos.findByTorneoIdOrderByCreadoEnAsc(torneoId)) {
                if (equipo.ia()) {
                    continue;
                }
                boolean devuelve = equipo.inscrito() && equipo.reservaId() != null;
                if (devuelve) {
                    nuevas.add(Operacion.devolucion(torneoId, equipo.id(), equipo.pagadoPor(), equipo.reservaId(),
                            torneo.costoInscripcion(), ahora));
                }
                nuevas.addAll(hitos.cancelacion(torneo, equipo, motivo.strip(), devuelve, ahora));
            }
            torneo.cancelar(motivo.strip(), ahora);
            torneos.save(torneo);
            guardarNuevas(nuevas);
            BITACORA.info("Torneo cancelado: id={} por={} motivo={} devoluciones={}", torneoId, actor.id(), motivo,
                    nuevas.stream().filter(o -> o.tipo() == Operacion.Tipo.DEVOLUCION_INSCRIPCION).count());
        });
        procesador.procesarDelTorneo(torneoId, EnumSet.of(Operacion.Tipo.DEVOLUCION_INSCRIPCION));
        return obtener(torneoId);
    }

    // ---------------------------------------------------------------- RF-TOR-003

    /**
     * Registra un equipo. La lista negra (HTTP) se consulta antes de abrir la
     * transaccion; dentro, con la fila del torneo bloqueada, se vuelve a
     * comprobar que el torneo siga abierto y que ningun integrante haya entrado
     * a otro equipo mientras tanto (un jugador, un equipo por torneo).
     */
    public Equipo crearEquipo(Actor actor, UUID torneoId, SolicitudDeEquipo solicitud) {
        exigirUsuario(actor);
        if (solicitud.nombre() == null || solicitud.nombre().strip().length() < 3
                || solicitud.avatar() == null || solicitud.avatar().isBlank() || solicitud.companeroUid() == null) {
            throw new TorneoRechazado(Motivo.SOLICITUD_INVALIDA, "hace falta el nombre, el avatar y el companero");
        }
        if (actor.id().equals(solicitud.companeroUid())) {
            throw new TorneoRechazado(Motivo.SOLICITUD_INVALIDA, "el equipo es de dos jugadores distintos");
        }
        Torneo torneo = torneoDe(torneoId);
        if (!torneo.admiteInscripciones()) {
            throw new TorneoRechazado(Motivo.ESTADO_NO_PERMITE, "las inscripciones de este torneo estan cerradas");
        }
        List<Equipo> delTorneo = equipos.findByTorneoIdOrderByCreadoEnAsc(torneoId);
        exigirLibre(delTorneo, actor.id(), null);
        exigirLibre(delTorneo, solicitud.companeroUid(), null);
        if (!filtro.aprobado(solicitud.nombre())) {
            throw new TorneoRechazado(Motivo.NOMBRE_RECHAZADO, "el nombre del equipo no cumple la politica de nombres");
        }
        if (!filtro.aprobado(solicitud.avatar())) {
            throw new TorneoRechazado(Motivo.NOMBRE_RECHAZADO, "el avatar del equipo no cumple la politica de nombres");
        }
        return transaccion.execute(estado -> {
            Torneo bloqueado = bloquear(torneoId);
            if (!bloqueado.admiteInscripciones()) {
                throw new TorneoRechazado(Motivo.ESTADO_NO_PERMITE, "las inscripciones de este torneo estan cerradas");
            }
            List<Equipo> actuales = equipos.findByTorneoIdOrderByCreadoEnAsc(torneoId);
            exigirLibre(actuales, actor.id(), null);
            exigirLibre(actuales, solicitud.companeroUid(), null);
            Equipo equipo = Equipo.deJugadores(UUID.randomUUID(), torneoId, solicitud.nombre(), solicitud.avatar(),
                    actor.id(), solicitud.companeroUid(), ahora());
            equipos.save(equipo);
            BITACORA.info("Equipo registrado: torneo={} equipo={} capitan={} companero={}", torneoId, equipo.id(),
                    actor.id(), solicitud.companeroUid());
            return equipo;
        });
    }

    public Equipo sustituirCompanero(Actor actor, UUID torneoId, UUID equipoId, UUID companeroUid) {
        exigirUsuario(actor);
        if (companeroUid == null) {
            throw new TorneoRechazado(Motivo.SOLICITUD_INVALIDA, "hace falta el nuevo companero");
        }
        return transaccion.execute(estado -> {
            Torneo torneo = bloquear(torneoId);
            Equipo equipo = equipoDe(torneoId, equipoId);
            if (!equipo.esCapitan(actor.id())) {
                throw new TorneoRechazado(Motivo.PERMISO_INSUFICIENTE, "solo el capitan sustituye a su companero");
            }
            if (!torneo.admiteInscripciones() || equipo.inscrito()) {
                throw new TorneoRechazado(Motivo.ESTADO_NO_PERMITE, "ya no se puede sustituir: el equipo esta inscrito o el torneo cerro");
            }
            if (actor.id().equals(companeroUid)) {
                throw new TorneoRechazado(Motivo.SOLICITUD_INVALIDA, "el capitan no puede ser su propio companero");
            }
            exigirLibre(equipos.findByTorneoIdOrderByCreadoEnAsc(torneoId), companeroUid, equipoId);
            equipo.sustituirCompanero(companeroUid);
            return equipos.save(equipo);
        });
    }

    // ---------------------------------------------------------------- RF-TOR-002

    /**
     * Inscribe al equipo pagando (D-23: paga el integrante que inscribe).
     *
     * <ol>
     *   <li>Comprobaciones rapidas sin bloquear (integrante, abierto, cupo).</li>
     *   <li>Fuera de toda transaccion: sanciones de los dos integrantes y la
     *       reserva en el libro con clave
     *       {@code torneo-<id>-jugador-<uid>-inscripcion}. Si el mismo jugador
     *       reintenta tras una caida, recibe la misma reserva y no aparta dos
     *       veces.</li>
     *   <li>Con la fila del torneo bloqueada: se repiten las comprobaciones y se
     *       confirma la inscripcion con su posicion.</li>
     * </ol>
     * Si el paso 3 rechaza (el cupo se lo llevo otro equipo, el torneo cerro, el
     * companero inscribio antes), la reserva recien hecha se libera; si el libro
     * no responde en ese momento, queda una devolucion pendiente para la tarea
     * programada. Nadie se queda con creditos apartados sin estar inscrito.
     */
    public Equipo inscribir(Actor actor, UUID torneoId, UUID equipoId) {
        exigirUsuario(actor);
        Torneo torneo = torneoDe(torneoId);
        Equipo equipo = equipoDe(torneoId, equipoId);
        comprobarInscripcion(actor, torneo, equipo, equipos.findByTorneoIdOrderByCreadoEnAsc(torneoId));
        for (UUID integrante : equipo.integrantes()) {
            if (sanciones.sancionado(integrante)) {
                throw new TorneoRechazado(Motivo.INTEGRANTE_SANCIONADO,
                        "un integrante tiene una sancion activa y el equipo no puede inscribirse");
            }
        }
        LibroDeCreditos.Reserva reserva = null;
        if (torneo.costoInscripcion() > 0) {
            reserva = libro.reservar(actor.id(), torneo.costoInscripcion(),
                    Operacion.clave(torneoId, actor.id(), "inscripcion"), "torneo-" + torneoId);
            if (!reserva.activa()) {
                throw new TorneoRechazado(Motivo.ESTADO_NO_PERMITE,
                        "la reserva de esta inscripcion ya se cerro en el libro (" + reserva.estado() + ")");
            }
        }
        UUID reservaId = reserva == null ? null : reserva.id();
        try {
            return transaccion.execute(estado -> confirmarInscripcion(actor, torneoId, equipoId, reservaId));
        } catch (TorneoRechazado rechazo) {
            if (reservaId != null) {
                devolverReservaSinInscripcion(torneoId, equipoId, actor.id(), reservaId, torneo.costoInscripcion());
            }
            throw rechazo;
        }
    }

    private Equipo confirmarInscripcion(Actor actor, UUID torneoId, UUID equipoId, UUID reservaId) {
        Torneo torneo = bloquear(torneoId);
        Equipo equipo = equipoDe(torneoId, equipoId);
        List<Equipo> delTorneo = equipos.findByTorneoIdOrderByCreadoEnAsc(torneoId);
        comprobarInscripcion(actor, torneo, equipo, delTorneo);
        int posicion = primeraPosicionLibre(delTorneo);
        equipo.inscribir(posicion, actor.id(), reservaId);
        equipos.save(equipo);
        guardarNuevas(hitos.inscripcion(torneo, equipo, ahora()));
        BITACORA.info("Equipo inscrito: torneo={} equipo={} posicion={} pagadoPor={} reserva={}", torneoId, equipoId,
                posicion, actor.id(), reservaId);
        return equipo;
    }

    private static void comprobarInscripcion(Actor actor, Torneo torneo, Equipo equipo, List<Equipo> delTorneo) {
        if (!equipo.tieneIntegrante(actor.id())) {
            throw new TorneoRechazado(Motivo.PERMISO_INSUFICIENTE, "solo un integrante inscribe a su equipo");
        }
        if (!torneo.admiteInscripciones()) {
            throw new TorneoRechazado(Motivo.ESTADO_NO_PERMITE, "las inscripciones de este torneo estan cerradas");
        }
        if (equipo.inscrito()) {
            throw new TorneoRechazado(Motivo.YA_INSCRITO, "el equipo ya esta inscrito");
        }
        long inscritos = delTorneo.stream().filter(Equipo::inscrito).count();
        if (inscritos >= Torneo.CUPOS) {
            throw new TorneoRechazado(Motivo.CUPO_AGOTADO, "el torneo ya tiene sus " + Torneo.CUPOS + " equipos");
        }
    }

    /**
     * La reserva se hizo pero la inscripcion no se confirmo: se libera ya, y si
     * el libro no responde queda una devolucion persistida que reintentara la
     * tarea programada.
     */
    private void devolverReservaSinInscripcion(UUID torneoId, UUID equipoId, UUID pagador, UUID reservaId, int monto) {
        try {
            libro.liberar(reservaId);
            BITACORA.info("Reserva liberada tras inscripcion rechazada: torneo={} reserva={}", torneoId, reservaId);
        } catch (FalloDeIntegracion fallo) {
            transaccion.executeWithoutResult(estado -> guardarNuevas(List.of(
                    Operacion.devolucion(torneoId, equipoId, pagador, reservaId, monto, ahora()))));
            BITACORA.warn("No se pudo liberar la reserva {} tras una inscripcion rechazada; queda pendiente: {}",
                    reservaId, fallo.getMessage());
        }
    }

    // ---------------------------------------------------------------- RF-TOR-004 / 005

    /**
     * Cierra inscripciones, completa con la maquina, genera el arbol y deja un
     * cobro persistido por cada equipo pagado, todo en una transaccion con la
     * fila del torneo bloqueada: un segundo inicio simultaneo espera, encuentra
     * el torneo EN_CURSO y recibe ESTADO_NO_PERMITE. Los cobros se ejecutan
     * despues de confirmar; lo que falte lo reintenta la tarea programada.
     */
    public TorneoCompleto iniciar(Actor actor, UUID torneoId) {
        exigirAdministrador(actor, "solo un administrador inicia el torneo");
        transaccion.executeWithoutResult(estado -> {
            Torneo torneo = bloquear(torneoId);
            if (!torneo.admiteInscripciones()) {
                throw new TorneoRechazado(Motivo.ESTADO_NO_PERMITE, "el torneo no esta en inscripciones abiertas");
            }
            List<Equipo> delTorneo = equipos.findByTorneoIdOrderByCreadoEnAsc(torneoId);
            List<Equipo> inscritos = new ArrayList<>(delTorneo.stream().filter(Equipo::inscrito).toList());
            if (inscritos.isEmpty()) {
                throw new TorneoRechazado(Motivo.SIN_EQUIPOS, "sin ningun equipo inscrito el torneo no puede empezar");
            }
            OffsetDateTime ahora = ahora();
            List<Equipo> humanos = List.copyOf(inscritos);
            List<Operacion> nuevas = new ArrayList<>();
            for (Equipo equipo : humanos) {
                if (equipo.reservaId() != null) {
                    nuevas.add(Operacion.cobro(torneoId, equipo.id(), equipo.pagadoPor(), equipo.reservaId(),
                            torneo.costoInscripcion(), ahora));
                }
            }
            int maquinas = 0;
            while (inscritos.size() < Torneo.CUPOS) {
                maquinas++;
                Equipo maquina = Equipo.deLaMaquina(UUID.randomUUID(), torneoId, maquinas,
                        primeraPosicionLibre(inscritos), ahora);
                equipos.save(maquina);
                inscritos.add(maquina);
            }
            inscritos.sort((a, b) -> Integer.compare(a.posicion(), b.posicion()));
            List<Encuentro> arbol = Arbol.generar(torneoId, inscritos);
            encuentros.saveAll(arbol);
            torneo.iniciar(ahora);
            torneos.save(torneo);
            for (Equipo equipo : humanos) {
                nuevas.addAll(hitos.inicio(torneo, equipo, primerEncuentro(arbol, equipo.id()), ahora));
            }
            guardarNuevas(nuevas);
            BITACORA.info("Torneo iniciado: id={} por={} humanos={} maquinas={} cobros={}", torneoId, actor.id(),
                    humanos.size(), maquinas, nuevas.stream().filter(o -> o.tipo() == Operacion.Tipo.COBRO_INSCRIPCION).count());
        });
        procesador.procesarDelTorneo(torneoId, EnumSet.of(Operacion.Tipo.COBRO_INSCRIPCION));
        return obtener(torneoId);
    }

    public TorneoCompleto registrarResultado(Actor actor, UUID torneoId, int numero, SolicitudDeResultado solicitud) {
        if (!actor.esServicio() && !actor.puedeAdministrar()) {
            throw new TorneoRechazado(Motivo.PERMISO_INSUFICIENTE,
                    "el resultado lo aporta la partida jugada o un administrador con motivo");
        }
        if (solicitud.ganadorEquipoId() == null && solicitud.ganadorUid() == null) {
            throw new TorneoRechazado(Motivo.SOLICITUD_INVALIDA, "hace falta el equipo ganador o el uid del jugador ganador");
        }
        if (!actor.esServicio() && (solicitud.motivo() == null || solicitud.motivo().isBlank())) {
            throw new TorneoRechazado(Motivo.SOLICITUD_INVALIDA,
                    "un administrador registra un resultado a mano solo con motivo (RF-ADM-005)");
        }
        Boolean hayCampeon = transaccion.execute(estado -> {
            Torneo torneo = bloquear(torneoId);
            if (!torneo.enCurso()) {
                throw new TorneoRechazado(Motivo.ESTADO_NO_PERMITE, "el torneo no esta en curso");
            }
            List<Encuentro> arbol = encuentros.findByTorneoIdOrderByNumeroAsc(torneoId);
            Map<UUID, Equipo> porId = equipos.findByTorneoIdOrderByCreadoEnAsc(torneoId).stream()
                    .collect(Collectors.toMap(Equipo::id, Function.identity()));
            UUID ganador = solicitud.ganadorEquipoId() != null
                    ? solicitud.ganadorEquipoId()
                    : equipoDelJugadorEn(arbol, porId, numero, solicitud.ganadorUid());
            Arbol.Movimiento movimiento;
            try {
                movimiento = Arbol.aplicar(arbol, porId, numero, ganador, solicitud.partidaId(),
                        actor.nombre(), actor.esServicio() ? null : solicitud.motivo().strip(), ahora());
            } catch (IllegalStateException noListo) {
                throw new TorneoRechazado(Motivo.ENCUENTRO_NO_LISTO, noListo.getMessage());
            } catch (IllegalArgumentException noParticipa) {
                throw new TorneoRechazado(Motivo.GANADOR_NO_PARTICIPA, noParticipa.getMessage());
            }
            encuentros.saveAll(arbol);
            equipos.saveAll(porId.values());
            movimiento.campeon().ifPresent(campeon -> {
                torneo.finalizar(campeon, ahora());
                torneos.save(torneo);
                guardarNuevas(premiosDelCampeon(torneo, porId.get(campeon)));
                BITACORA.info("Torneo finalizado: id={} campeon={}", torneoId, campeon);
            });
            BITACORA.info("Resultado registrado: torneo={} encuentro={} ganador={} por={} motivo={}", torneoId, numero,
                    ganador, actor.nombre(), solicitud.motivo());
            return movimiento.campeon().isPresent();
        });
        if (Boolean.TRUE.equals(hayCampeon)) {
            procesador.procesarDelTorneo(torneoId, EnumSet.of(Operacion.Tipo.PREMIO));
        }
        return obtener(torneoId);
    }

    /**
     * El premio de cada integrante del equipo campeon (RF-TOR-007). Un equipo
     * de la maquina no recibe nada: no tiene cuenta en el libro ni inventario
     * (mismo criterio que D-18). Si el premio del torneo es cero y sin epica,
     * tampoco hay nada que entregar.
     */
    private List<Operacion> premiosDelCampeon(Torneo torneo, Equipo campeon) {
        if (campeon == null || campeon.ia()) {
            return List.of();
        }
        PoliticaDePremio.Premio premio = torneo.premio(premios);
        if (premio.vacio()) {
            return List.of();
        }
        OffsetDateTime ahora = ahora();
        return campeon.integrantes().stream()
                .map(uid -> Operacion.premio(torneo.id(), campeon.id(), uid, premio.creditosPorIntegrante(),
                        premio.epicaProductoId(), ahora))
                .toList();
    }

    // ---------------------------------------------------------------- RF-ADM-005

    /**
     * Vuelve a poner en la cola las operaciones del torneo que quedaron para
     * revision, con su misma clave (repetirlas no duplica nada), y las intenta.
     */
    public TorneoCompleto reintentarOperaciones(Actor actor, UUID torneoId) {
        exigirAdministrador(actor, "solo un administrador reintenta las operaciones de un torneo");
        torneoDe(torneoId);
        transaccion.executeWithoutResult(estado -> {
            OffsetDateTime ahora = ahora();
            List<Operacion> fallidas = operaciones.findByTorneoIdAndEstado(torneoId, Operacion.Estado.FALLIDA);
            fallidas.forEach(op -> op.reabrir(ahora));
            operaciones.saveAll(fallidas);
            BITACORA.info("Operaciones reabiertas: torneo={} por={} cuantas={}", torneoId, actor.id(), fallidas.size());
        });
        procesador.procesarDelTorneo(torneoId, ProcesadorDeOperaciones.TODAS);
        return obtener(torneoId);
    }

    // ---------------------------------------------------------------- apoyo

    private TorneoCompleto completar(Torneo torneo) {
        return new TorneoCompleto(torneo, equipos.findByTorneoIdOrderByCreadoEnAsc(torneo.id()),
                encuentros.findByTorneoIdOrderByNumeroAsc(torneo.id()),
                operaciones.findByTorneoIdOrderByCreadaEnAsc(torneo.id()), torneo.premio(premios));
    }

    /** Guarda las operaciones nuevas que todavia no existan (la clave es unica). */
    private void guardarNuevas(List<Operacion> nuevas) {
        for (Operacion operacion : nuevas) {
            if (!operaciones.existsByClave(operacion.clave())) {
                operaciones.save(operacion);
            }
        }
    }

    private static Integer primerEncuentro(List<Encuentro> arbol, UUID equipoId) {
        return arbol.stream().filter(e -> e.participa(equipoId)).map(Encuentro::numero).findFirst().orElse(null);
    }

    /**
     * El equipo del encuentro {@code numero} en el que juega {@code uid}
     * (contrato 1.1.0). Si no juega ahi, GANADOR_NO_PARTICIPA: la partida no
     * puede decidir un encuentro que no era suyo.
     */
    private static UUID equipoDelJugadorEn(List<Encuentro> arbol, Map<UUID, Equipo> porId, int numero, UUID uid) {
        if (numero < 1 || numero > arbol.size()) {
            throw new TorneoRechazado(Motivo.NO_ENCONTRADO, "no hay encuentro " + numero);
        }
        Encuentro encuentro = arbol.get(numero - 1);
        return Stream.of(encuentro.equipoA(), encuentro.equipoB())
                .filter(Objects::nonNull)
                .map(porId::get)
                .filter(e -> e != null && e.tieneIntegrante(uid))
                .map(Equipo::id)
                .findFirst()
                .orElseThrow(() -> new TorneoRechazado(Motivo.GANADOR_NO_PARTICIPA,
                        "el jugador " + uid + " no juega el encuentro " + numero));
    }

    private Torneo torneoDe(UUID id) {
        return torneos.findById(id).orElseThrow(() ->
                new TorneoRechazado(Motivo.NO_ENCONTRADO, "no hay ningun torneo " + id));
    }

    private Torneo bloquear(UUID id) {
        return torneos.bloquear(id).orElseThrow(() ->
                new TorneoRechazado(Motivo.NO_ENCONTRADO, "no hay ningun torneo " + id));
    }

    private Equipo equipoDe(UUID torneoId, UUID equipoId) {
        return equipos.findById(equipoId).filter(e -> e.torneoId().equals(torneoId)).orElseThrow(() ->
                new TorneoRechazado(Motivo.NO_ENCONTRADO, "no hay ningun equipo " + equipoId + " en ese torneo"));
    }

    private static void exigirLibre(List<Equipo> delTorneo, UUID jugador, UUID salvoEquipo) {
        boolean ocupado = delTorneo.stream()
                .filter(e -> salvoEquipo == null || !e.id().equals(salvoEquipo))
                .anyMatch(e -> e.tieneIntegrante(jugador));
        if (ocupado) {
            throw new TorneoRechazado(Motivo.JUGADOR_YA_EN_EQUIPO, "el jugador " + jugador + " ya esta en un equipo de este torneo");
        }
    }

    private static int primeraPosicionLibre(List<Equipo> delTorneo) {
        for (int posicion = 1; posicion <= Torneo.CUPOS; posicion++) {
            int candidata = posicion;
            if (delTorneo.stream().noneMatch(e -> Objects.equals(e.posicion(), candidata))) {
                return posicion;
            }
        }
        throw new TorneoRechazado(Motivo.CUPO_AGOTADO, "no queda ninguna posicion libre");
    }

    private static void exigirAdministrador(Actor actor, String detalle) {
        if (!actor.esUsuario() || !actor.puedeAdministrar()) {
            throw new TorneoRechazado(Motivo.PERMISO_INSUFICIENTE, detalle);
        }
    }

    private static void exigirUsuario(Actor actor) {
        if (!actor.esUsuario()) {
            throw new TorneoRechazado(Motivo.PERMISO_INSUFICIENTE, "esta accion es de un jugador, no de un servicio");
        }
    }

    private OffsetDateTime ahora() {
        return OffsetDateTime.now(reloj);
    }
}
