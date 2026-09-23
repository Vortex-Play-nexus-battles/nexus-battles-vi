package com.nexusbattles.plataforma.torneos.torneo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.nexusbattles.plataforma.torneos.torneo.TorneoRechazado.Motivo;

/**
 * Casos de uso de M13 (corte vertical del Sprint 3): crear y cancelar un
 * torneo, registrar equipos, inscribirlos pagando, iniciar (rellenar con la
 * maquina y generar el arbol) y registrar resultados.
 */
@Service
public class TorneosService {

    private static final Logger BITACORA = LoggerFactory.getLogger(TorneosService.class);

    private final TorneoRepository torneos;
    private final EquipoRepository equipos;
    private final EncuentroRepository encuentros;
    private final LibroDeCreditos libro;
    private final FiltroDeNombres filtro;
    private final ConsultaDeSanciones sanciones;
    private final Clock reloj;

    public TorneosService(TorneoRepository torneos, EquipoRepository equipos, EncuentroRepository encuentros,
                          LibroDeCreditos libro, FiltroDeNombres filtro, ConsultaDeSanciones sanciones, Clock reloj) {
        this.torneos = torneos;
        this.equipos = equipos;
        this.encuentros = encuentros;
        this.libro = libro;
        this.filtro = filtro;
        this.sanciones = sanciones;
        this.reloj = reloj;
    }

    /** Torneo con todo lo que la vista necesita. */
    public record TorneoCompleto(Torneo torneo, List<Equipo> equipos, List<Encuentro> encuentros) {
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
        return torneos.findAllByOrderByCreadoEnDesc().stream().map(this::completar).toList();
    }

    @Transactional(readOnly = true)
    public TorneoCompleto obtener(UUID torneoId) {
        return completar(torneoDe(torneoId));
    }

    // ---------------------------------------------------------------- RF-TOR-001

    @Transactional
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
                solicitud.inscripcionesCierranEn(), costo);
        torneos.save(torneo);
        BITACORA.info("Torneo creado: id={} nombre={} por={} costo={}", torneo.id(), torneo.nombre(), actor.id(), costo);
        return completar(torneo);
    }

    @Transactional
    public TorneoCompleto cancelar(Actor actor, UUID torneoId, String motivo) {
        exigirAdministrador(actor, "solo un administrador cancela torneos");
        if (motivo == null || motivo.isBlank()) {
            throw new TorneoRechazado(Motivo.SOLICITUD_INVALIDA, "la cancelacion lleva su motivo");
        }
        Torneo torneo = torneoDe(torneoId);
        if (!torneo.admiteInscripciones()) {
            throw new TorneoRechazado(Motivo.ESTADO_NO_PERMITE, "solo se cancela un torneo antes de que empiece");
        }
        // Devolucion por el mismo medio (CA-04 de RF-TOR-002): primero el
        // libro; si no responde, nada cambia y se reintenta mas tarde.
        for (Equipo equipo : equipos.findByTorneoIdOrderByCreadoEnAsc(torneoId)) {
            if (equipo.reservaId() != null) {
                libro.liberar(equipo.reservaId());
            }
        }
        torneo.cancelar(motivo.strip(), ahora());
        torneos.save(torneo);
        BITACORA.info("Torneo cancelado: id={} por={} motivo={}", torneoId, actor.id(), motivo);
        return completar(torneo);
    }

    // ---------------------------------------------------------------- RF-TOR-003

    @Transactional
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
        Equipo equipo = Equipo.deJugadores(UUID.randomUUID(), torneoId, solicitud.nombre(), solicitud.avatar(),
                actor.id(), solicitud.companeroUid(), ahora());
        equipos.save(equipo);
        BITACORA.info("Equipo registrado: torneo={} equipo={} capitan={} companero={}", torneoId, equipo.id(),
                actor.id(), solicitud.companeroUid());
        return equipo;
    }

    @Transactional
    public Equipo sustituirCompanero(Actor actor, UUID torneoId, UUID equipoId, UUID companeroUid) {
        exigirUsuario(actor);
        if (companeroUid == null) {
            throw new TorneoRechazado(Motivo.SOLICITUD_INVALIDA, "hace falta el nuevo companero");
        }
        Torneo torneo = torneoDe(torneoId);
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
    }

    // ---------------------------------------------------------------- RF-TOR-002

    @Transactional
    public Equipo inscribir(Actor actor, UUID torneoId, UUID equipoId) {
        exigirUsuario(actor);
        Torneo torneo = torneoDe(torneoId);
        Equipo equipo = equipoDe(torneoId, equipoId);
        if (!equipo.tieneIntegrante(actor.id())) {
            throw new TorneoRechazado(Motivo.PERMISO_INSUFICIENTE, "solo un integrante inscribe a su equipo");
        }
        if (!torneo.admiteInscripciones()) {
            throw new TorneoRechazado(Motivo.ESTADO_NO_PERMITE, "las inscripciones de este torneo estan cerradas");
        }
        if (equipo.inscrito()) {
            throw new TorneoRechazado(Motivo.YA_INSCRITO, "el equipo ya esta inscrito");
        }
        List<Equipo> delTorneo = equipos.findByTorneoIdOrderByCreadoEnAsc(torneoId);
        long inscritos = delTorneo.stream().filter(Equipo::inscrito).count();
        if (inscritos >= Torneo.CUPOS) {
            throw new TorneoRechazado(Motivo.CUPO_AGOTADO, "el torneo ya tiene sus " + Torneo.CUPOS + " equipos");
        }
        for (UUID integrante : equipo.integrantes()) {
            if (sanciones.sancionado(integrante)) {
                throw new TorneoRechazado(Motivo.INTEGRANTE_SANCIONADO,
                        "un integrante tiene una sancion activa y el equipo no puede inscribirse");
            }
        }
        UUID reserva = null;
        if (torneo.costoInscripcion() > 0) {
            reserva = libro.reservar(actor.id(), torneo.costoInscripcion(),
                    "torneo-" + torneoId + "-equipo-" + equipoId, "torneo-" + torneoId);
        }
        int posicion = primeraPosicionLibre(delTorneo);
        equipo.inscribir(posicion, actor.id(), reserva);
        equipos.save(equipo);
        BITACORA.info("Equipo inscrito: torneo={} equipo={} posicion={} pagadoPor={} reserva={}", torneoId, equipoId,
                posicion, actor.id(), reserva);
        return equipo;
    }

    // ---------------------------------------------------------------- RF-TOR-004 / 005

    @Transactional
    public TorneoCompleto iniciar(Actor actor, UUID torneoId) {
        exigirAdministrador(actor, "solo un administrador inicia el torneo");
        Torneo torneo = torneoDe(torneoId);
        if (!torneo.admiteInscripciones()) {
            throw new TorneoRechazado(Motivo.ESTADO_NO_PERMITE, "el torneo no esta en inscripciones abiertas");
        }
        List<Equipo> delTorneo = equipos.findByTorneoIdOrderByCreadoEnAsc(torneoId);
        List<Equipo> inscritos = new ArrayList<>(delTorneo.stream().filter(Equipo::inscrito).toList());
        if (inscritos.isEmpty()) {
            throw new TorneoRechazado(Motivo.SIN_EQUIPOS, "sin ningun equipo inscrito el torneo no puede empezar");
        }
        // Primero el cobro: si el libro no responde, nada cambia (CA-03 de RF-TOR-007 aplica igual aqui).
        for (Equipo equipo : inscritos) {
            if (equipo.reservaId() != null) {
                libro.consumir(equipo.reservaId());
            }
        }
        OffsetDateTime ahora = ahora();
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
        BITACORA.info("Torneo iniciado: id={} por={} humanos={} maquinas={}", torneoId, actor.id(),
                inscritos.size() - maquinas, maquinas);
        return completar(torneo);
    }

    @Transactional
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
        Torneo torneo = torneoDe(torneoId);
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
            BITACORA.info("Torneo finalizado: id={} campeon={}", torneoId, campeon);
        });
        BITACORA.info("Resultado registrado: torneo={} encuentro={} ganador={} por={} motivo={}", torneoId, numero,
                ganador, actor.nombre(), solicitud.motivo());
        return completar(torneo);
    }

    // ---------------------------------------------------------------- apoyo

    private TorneoCompleto completar(Torneo torneo) {
        return new TorneoCompleto(torneo, equipos.findByTorneoIdOrderByCreadoEnAsc(torneo.id()),
                encuentros.findByTorneoIdOrderByNumeroAsc(torneo.id()));
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
