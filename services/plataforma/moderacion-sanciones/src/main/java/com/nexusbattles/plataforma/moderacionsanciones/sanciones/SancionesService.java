package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Reglas de las sanciones y sus apelaciones — HU-USR-004/005/006/007.
 *
 * <p><b>Quien puede que</b> (Tabla 24 del documento oficial): moderador,
 * administrador y super administrador emiten advertencias y suspensiones;
 * el moderador <b>solo</b> temporales, asi que el baneo es de administrador o
 * super administrador (CA-06 de HU-USR-005, historia de HU-USR-006). Un
 * jugador no sanciona a nadie. Las apelaciones las resuelve el panel:
 * administrador o super administrador.
 *
 * <p><b>Fuente de verdad unica (7.3.2, B2).</b> El estado de acceso de la
 * cuenta en ms-identidad ya no es un sistema paralelo: cada emision, cada
 * resolucion que cambia el acceso y cada levantamiento se proyectan sobre la
 * cuenta, y el jugador recibe el aviso en la app y el correo. Todo eso sale
 * por {@link SalidasDeSancion}, en la misma transaccion que la sancion, y lo
 * entrega {@link EntregadorDeSalidas} con reintento: la sancion queda
 * registrada aunque identidad o correo esten caidos. Tampoco escala
 * advertencias a suspension (CA-05 de HU-USR-004, decision del PO).
 *
 * <p><b>Sobre quien (7.3.1).</b> La Tabla 24 dice que puede hacer cada rol;
 * la jerarquia dice sobre quien: emitir, levantar y resolver la apelacion solo
 * sobre una cuenta de rol estrictamente inferior al de quien actua. Antes un
 * moderador podia suspender a un administrador y un administrador banear al
 * ultimo Super Administrador. El rol se pregunta a ms-identidad en el momento
 * ({@link RolesDeCuentas}); si no responde, no se registra nada (503): una
 * sancion puede esperar unos segundos, una sancion indebida no se deshace sola.
 */
@Service
public class SancionesService {

    private static final Logger BITACORA = LoggerFactory.getLogger(SancionesService.class);

    /** {@code minLength} del motivo de un levantamiento (moderacion-sanciones-admin.yaml 1.1.x). */
    static final int MOTIVO_MINIMO_DEL_LEVANTAMIENTO = 3;

    /** {@code maxLength} de los motivos del contrato. */
    static final int MOTIVO_MAXIMO = 1000;

    private final SancionRepository sanciones;
    private final ApelacionRepository apelaciones;
    private final SalidasDeSancion salidas;
    private final Clock reloj;
    private final LimitesDeSancion limites;
    private final RolesDeCuentas roles;
    private final PlazoDeDecision plazoDeDecision;

    @org.springframework.beans.factory.annotation.Autowired
    public SancionesService(SancionRepository sanciones, ApelacionRepository apelaciones,
                            SalidasDeSancion salidas, Clock reloj, LimitesDeSancion limites,
                            RolesDeCuentas roles, PlazoDeDecision plazoDeDecision) {
        this.sanciones = Objects.requireNonNull(sanciones);
        this.apelaciones = Objects.requireNonNull(apelaciones);
        this.salidas = Objects.requireNonNull(salidas);
        this.reloj = Objects.requireNonNull(reloj);
        this.limites = Objects.requireNonNull(limites);
        this.roles = Objects.requireNonNull(roles);
        this.plazoDeDecision = Objects.requireNonNull(plazoDeDecision);
    }

    /** Sin festivos configurados: el plazo de decision cuenta de lunes a viernes. */
    public SancionesService(SancionRepository sanciones, ApelacionRepository apelaciones,
                            SalidasDeSancion salidas, Clock reloj, LimitesDeSancion limites,
                            RolesDeCuentas roles) {
        this(sanciones, apelaciones, salidas, reloj, limites, roles, PlazoDeDecision.deLunesAViernes());
    }

    /** Limites fijos (pruebas): rango de la suspension y plazo de apelacion de 30 dias. */
    public SancionesService(SancionRepository sanciones, ApelacionRepository apelaciones,
                            SalidasDeSancion salidas, Clock reloj, long minimaHoras, long maximaDias,
                            RolesDeCuentas roles) {
        this(sanciones, apelaciones, salidas, reloj, LimitesDeSancion.Fijos.de(minimaHoras, maximaDias, 30), roles);
    }

    /**
     * Los limites vigentes, para que la interfaz diga el mismo numero que
     * aplica el servicio (HU-ADM-001 CA-04).
     *
     * <p>No es un detalle de presentacion: el plazo de apelacion y el rango de
     * la suspension son configurables, y una pantalla que los tenga escritos a
     * mano miente en cuanto el Product Owner los cambie. Cada llamada pasa por
     * la cache del lector de parametros, asi que preguntar no cuesta una
     * peticion de red.
     */
    public LimitesDeSancion limitesVigentes() {
        return limites;
    }

    /**
     * Lo que se pide al emitir. {@code confirmacion} solo se mira en el baneo
     * (CA-01 de HU-USR-006). {@code causal} (SA-4, §7.3.2) es opcional en la
     * API para no romper a quien ya emite; la consola la exige.
     */
    public record SolicitudDeSancion(UUID usuarioId, Sancion.Tipo tipo, Causal causal, String motivo,
                                     String politica, String comentarioId, Long duracionHoras,
                                     boolean confirmacion) {

        /** Sin causal: como antes de SA-4. */
        public SolicitudDeSancion(UUID usuarioId, Sancion.Tipo tipo, String motivo, String politica,
                                  String comentarioId, Long duracionHoras, boolean confirmacion) {
            this(usuarioId, tipo, null, motivo, politica, comentarioId, duracionHoras, confirmacion);
        }
    }

    @Transactional
    public Sancion emitir(Actor actor, SolicitudDeSancion solicitud) {
        Objects.requireNonNull(actor);
        Objects.requireNonNull(solicitud);
        if (!actor.puedeModerar()) {
            throw new SancionRechazada(SancionRechazada.Motivo.PERMISO_INSUFICIENTE,
                    "solo moderadores y administradores emiten sanciones");
        }
        if (solicitud.usuarioId() == null || solicitud.tipo() == null) {
            throw new SancionRechazada(SancionRechazada.Motivo.SOLICITUD_INVALIDA, "hace falta el usuario y el tipo");
        }
        if (solicitud.motivo() == null || solicitud.motivo().isBlank()) {
            throw new SancionRechazada(SancionRechazada.Motivo.SOLICITUD_INVALIDA, "toda sancion lleva su motivo");
        }
        if (solicitud.usuarioId().equals(actor.id())) {
            throw new SancionRechazada(SancionRechazada.Motivo.SOLICITUD_INVALIDA, "nadie se sanciona a si mismo");
        }
        OffsetDateTime ahora = ahora();
        if (estaBaneado(solicitud.usuarioId(), ahora)) {
            throw new SancionRechazada(SancionRechazada.Motivo.USUARIO_BANEADO,
                    "el usuario ya esta baneado: no procede ninguna sancion mas");
        }

        OffsetDateTime vigenteHasta = null;
        switch (solicitud.tipo()) {
            case ADVERTENCIA -> { /* sin efecto sobre el acceso */ }
            case SUSPENSION -> vigenteHasta = ahora.plus(duracionValida(solicitud.duracionHoras()));
            case BANEO -> {
                if (!actor.puedeAdministrar()) {
                    throw new SancionRechazada(SancionRechazada.Motivo.PERMISO_INSUFICIENTE,
                            "un moderador solo emite sanciones temporales; el baneo es de administrador");
                }
                if (!solicitud.confirmacion()) {
                    throw new SancionRechazada(SancionRechazada.Motivo.SOLICITUD_INVALIDA,
                            "el baneo es definitivo: exige confirmacion explicita");
                }
            }
        }

        exigirJerarquia(actor, solicitud.usuarioId(), "sancionar a");

        Sancion sancion = new Sancion(UUID.randomUUID(), solicitud.usuarioId(), solicitud.tipo(), solicitud.causal(),
                solicitud.motivo().strip(), vacioANulo(solicitud.politica()), vacioANulo(solicitud.comentarioId()),
                actor.id(), actor.rol(), ahora, vigenteHasta);
        sanciones.save(sancion);
        BITACORA.info("Sancion emitida: id={} tipo={} causal={} usuario={} por={} ({}) vigenteHasta={}",
                sancion.id(), sancion.tipo(), sancion.causal(), sancion.usuarioId(), actor.id(), actor.rol(),
                vigenteHasta);
        salidas.emision(sancion, tituloDe(sancion), cuerpoDe(sancion), ahora);
        return sancion;
    }

    /**
     * Levanta una sancion vigente sin pasar por apelacion — el «reactivar» del
     * panel de usuarios (moderacion-sanciones-admin.yaml 1.1.x,
     * {@code POST /sanciones/{sancionId}/levantamiento}).
     *
     * <p>Solo ADMINISTRADOR o SUPER_ADMINISTRADOR: es la otra cara del baneo,
     * que tampoco puede emitir un moderador (Tabla 24). La sancion no se borra:
     * queda revertida con el motivo y el autor, igual que al revertirla una
     * apelacion (CA-04 de HU-USR-006). Si ya no estaba vigente (revertida o
     * suspension vencida) no hay nada que levantar: 409. Deja el aviso al
     * jugador y, si la sancion restringia el acceso, la proyeccion sobre su
     * cuenta ({@link SalidasDeSancion#levantamiento}).
     *
     * <p>Una apelacion que siguiera abierta sobre esta sancion no se toca: la
     * resuelve el panel, que vera la sancion ya revertida.
     *
     * <p><b>El baneo no se levanta (SA-4, §7.3.2).</b> Es una «inhabilitacion
     * permanente»: solo lo revierte una apelacion favorable, decidida por el
     * panel con su motivo. Hasta SA-4 un administrador podia levantarlo desde
     * aqui aunque ms-identidad ya se negaba a reactivar una cuenta baneada; las
     * dos puertas dicen ahora lo mismo (409).
     */
    @Transactional
    public Sancion levantar(Actor actor, UUID sancionId, String motivo) {
        if (!actor.puedeAdministrar()) {
            throw new SancionRechazada(SancionRechazada.Motivo.PERMISO_INSUFICIENTE,
                    "solo un administrador levanta una sancion");
        }
        String motivoLimpio = motivo == null ? "" : motivo.strip();
        if (motivoLimpio.length() < MOTIVO_MINIMO_DEL_LEVANTAMIENTO || motivoLimpio.length() > MOTIVO_MAXIMO) {
            throw new SancionRechazada(SancionRechazada.Motivo.SOLICITUD_INVALIDA,
                    "el levantamiento va motivado (de " + MOTIVO_MINIMO_DEL_LEVANTAMIENTO + " a " + MOTIVO_MAXIMO
                            + " caracteres)");
        }
        Sancion sancion = sanciones.findById(sancionId).orElseThrow(() ->
                new SancionRechazada(SancionRechazada.Motivo.NO_ENCONTRADA, "no hay ninguna sancion " + sancionId));
        OffsetDateTime ahora = ahora();
        if (!sancion.estaVigenteEn(ahora)) {
            throw new SancionRechazada(SancionRechazada.Motivo.SANCION_NO_VIGENTE,
                    "la sancion ya no esta vigente: no hay nada que levantar");
        }
        if (sancion.tipo() == Sancion.Tipo.BANEO) {
            throw new SancionRechazada(SancionRechazada.Motivo.BANEO_SOLO_POR_APELACION,
                    "el baneo es definitivo: solo lo revierte una apelacion favorable del panel");
        }
        exigirJerarquia(actor, sancion.usuarioId(), "levantar la sancion de");
        sancion.revertir(actor.id(), motivoLimpio, ahora);
        sanciones.save(sancion);
        BITACORA.info("Sancion levantada: id={} tipo={} usuario={} por={} ({})", sancion.id(), sancion.tipo(),
                sancion.usuarioId(), actor.id(), actor.rol());
        salidas.levantamiento(sancion, TextosDeSancion.tituloDelLevantamiento(sancion),
                TextosDeSancion.cuerpoDelLevantamiento(sancion, motivoLimpio), ahora);
        return sancion;
    }

    /** Filas por pagina del listado global: las dieciseis de los listados (RNF-USA-001), como mucho cien. */
    static final int TAMANO_MAXIMO_DE_PAGINA = 100;

    /**
     * El listado global de sanciones con sus filtros (SA-4, informe punto 20):
     * de la mas reciente a la mas antigua. Solo quien modera.
     */
    @Transactional(readOnly = true)
    public Page<Sancion> buscar(Actor actor, FiltroDeSanciones filtro, int pagina, int tamano) {
        if (!actor.puedeModerar()) {
            throw new SancionRechazada(SancionRechazada.Motivo.PERMISO_INSUFICIENTE,
                    "el listado de sanciones es de moderacion");
        }
        FiltroDeSanciones aplicado = filtro == null ? FiltroDeSanciones.ninguno() : filtro;
        if (aplicado.desde() != null && aplicado.hasta() != null && !aplicado.hasta().isAfter(aplicado.desde())) {
            throw new SancionRechazada(SancionRechazada.Motivo.SOLICITUD_INVALIDA,
                    "el periodo va de desde a hasta, con hasta posterior a desde");
        }
        if (pagina < 0 || tamano < 1 || tamano > TAMANO_MAXIMO_DE_PAGINA) {
            throw new SancionRechazada(SancionRechazada.Motivo.SOLICITUD_INVALIDA,
                    "la pagina empieza en 0 y lleva de 1 a " + TAMANO_MAXIMO_DE_PAGINA + " sanciones");
        }
        // El id desempata dos emitidas en el mismo instante: sin el, una fila
        // podria salir en dos paginas o en ninguna.
        return sanciones.findAll(aplicado.comoEspecificacion(ahora()),
                PageRequest.of(pagina, tamano, Sort.by(Sort.Order.desc("emitidaEn"), Sort.Order.asc("id"))));
    }

    /** Historial completo del usuario, del mas reciente al mas antiguo. Lo ve el propio usuario o quien modera. */
    @Transactional(readOnly = true)
    public List<Sancion> historialDe(Actor actor, UUID usuarioId) {
        if (!actor.puedeModerar() && !actor.id().equals(usuarioId)) {
            throw new SancionRechazada(SancionRechazada.Motivo.PERMISO_INSUFICIENTE, "el historial de otro no es tuyo");
        }
        return sanciones.findByUsuarioIdOrderByEmitidaEnDesc(usuarioId);
    }

    @Transactional(readOnly = true)
    public Sancion obtener(Actor actor, UUID sancionId) {
        Sancion sancion = sanciones.findById(sancionId).orElseThrow(() ->
                new SancionRechazada(SancionRechazada.Motivo.NO_ENCONTRADA, "no hay ninguna sancion " + sancionId));
        if (!actor.puedeModerar() && !actor.id().equals(sancion.usuarioId())) {
            throw new SancionRechazada(SancionRechazada.Motivo.PERMISO_INSUFICIENTE, "esa sancion no es tuya");
        }
        return sancion;
    }

    /**
     * La sancion que restringe hoy al usuario, si hay: el baneo antes que la
     * suspension, y de las suspensiones la que mas dure. Es lo que responde
     * {@code GET /sanciones/usuarios/{uid}/activa}. Una advertencia nunca
     * restringe (CA-02 de HU-USR-004).
     */
    @Transactional(readOnly = true)
    public java.util.Optional<Sancion> activaDe(UUID usuarioId) {
        OffsetDateTime ahora = ahora();
        return sanciones.findByUsuarioIdAndRevertidaEnIsNullAndTipoNot(usuarioId, Sancion.Tipo.ADVERTENCIA).stream()
                .filter(s -> s.restringeEn(ahora))
                .min((a, b) -> {
                    if (a.tipo() != b.tipo()) {
                        return a.tipo() == Sancion.Tipo.BANEO ? -1 : 1;
                    }
                    if (a.tipo() == Sancion.Tipo.BANEO) {
                        return a.emitidaEn().compareTo(b.emitidaEn());
                    }
                    return b.vigenteHasta().compareTo(a.vigenteHasta());
                });
    }

    /* ---- Apelaciones (HU-USR-007) ---- */

    @Transactional
    public Apelacion apelar(Actor actor, UUID sancionId, String argumento) {
        Sancion sancion = sanciones.findById(sancionId).orElseThrow(() ->
                new SancionRechazada(SancionRechazada.Motivo.NO_ENCONTRADA, "no hay ninguna sancion " + sancionId));
        if (!sancion.usuarioId().equals(actor.id())) {
            throw new SancionRechazada(SancionRechazada.Motivo.APELACION_NO_PROCEDE, "solo el sancionado apela su sancion");
        }
        if (argumento == null || argumento.isBlank()) {
            throw new SancionRechazada(SancionRechazada.Motivo.SOLICITUD_INVALIDA, "la apelacion lleva tu argumento");
        }
        OffsetDateTime ahora = ahora();
        if (!sancion.estaVigenteEn(ahora)) {
            throw new SancionRechazada(SancionRechazada.Motivo.APELACION_NO_PROCEDE,
                    "la sancion ya no esta vigente: no hay nada que apelar");
        }
        Duration plazo = limites.plazoDeApelacion();
        if (ahora.isAfter(sancion.emitidaEn().plus(plazo))) {
            throw new SancionRechazada(SancionRechazada.Motivo.APELACION_NO_PROCEDE,
                    "el plazo para apelar (" + plazo.toDays() + " dias desde la sancion) ya paso");
        }
        if (apelaciones.findBySancionIdAndEstado(sancionId, Apelacion.Estado.PENDIENTE).isPresent()) {
            throw new SancionRechazada(SancionRechazada.Motivo.APELACION_NO_PROCEDE,
                    "ya hay una apelacion abierta sobre esta sancion");
        }
        Apelacion apelacion = new Apelacion(UUID.randomUUID(), sancionId, actor.id(), argumento.strip(), ahora);
        apelaciones.save(apelacion);
        BITACORA.info("Apelacion abierta: id={} sancion={} usuario={}", apelacion.id(), sancionId, actor.id());
        // SA-4: el sancionado sabe que llego y hasta cuando tiene el panel
        // para responderle (§7.3.2, quince dias habiles).
        salidas.apelacionRecibida(apelacion, sancion, TextosDeSancion.tituloDeLaApelacionRecibida(),
                TextosDeSancion.cuerpoDeLaApelacionRecibida(sancion, plazoDeDecision.limitePara(ahora)), ahora);
        return apelacion;
    }

    /** Por que la sancion que impide entrar no se puede apelar ahora (SA-4, apelacion sin sesion). */
    public enum MotivoNoApelable { SIN_SANCION, FUERA_DE_PLAZO, APELACION_ABIERTA }

    /**
     * Lo que ve en la pantalla de inicio de sesion una cuenta suspendida o
     * baneada (SA-4): la sancion que le impide entrar, hasta cuando puede
     * apelarla y, si ya apelo, como va la ultima apelacion.
     *
     * @param sancion          la que restringe hoy; nula si ya no hay ninguna
     * @param apelableHasta    emision + plazo de apelacion vigente (§7.3.2, 30 dias)
     * @param motivoNoApelable nulo si se puede apelar ahora
     * @param apelacion        la ultima apelacion de esa sancion, abierta o resuelta
     */
    public record EstadoDeApelacion(Sancion sancion, OffsetDateTime apelableHasta,
                                    MotivoNoApelable motivoNoApelable, Apelacion apelacion) {

        public boolean puedeApelar() {
            return sancion != null && motivoNoApelable == null;
        }
    }

    /**
     * El estado de la apelacion de la sancion que hoy impide entrar a la
     * cuenta. Las mismas reglas que {@link #apelar}: dentro del plazo y sin
     * otra apelacion abierta sobre la misma sancion.
     */
    @Transactional(readOnly = true)
    public EstadoDeApelacion estadoDeApelacionDe(UUID usuarioId) {
        java.util.Optional<Sancion> activa = activaDe(Objects.requireNonNull(usuarioId));
        if (activa.isEmpty()) {
            return new EstadoDeApelacion(null, null, MotivoNoApelable.SIN_SANCION, null);
        }
        Sancion sancion = activa.get();
        OffsetDateTime apelableHasta = sancion.emitidaEn().plus(limites.plazoDeApelacion());
        Apelacion ultima = apelaciones.findFirstBySancionIdOrderByCreadaEnDesc(sancion.id()).orElse(null);
        MotivoNoApelable motivo = null;
        if (ultima != null && ultima.estado() == Apelacion.Estado.PENDIENTE) {
            motivo = MotivoNoApelable.APELACION_ABIERTA;
        } else if (ahora().isAfter(apelableHasta)) {
            motivo = MotivoNoApelable.FUERA_DE_PLAZO;
        }
        return new EstadoDeApelacion(sancion, apelableHasta, motivo, ultima);
    }

    /**
     * La apelacion de quien no puede iniciar sesion (SA-4, §7.3.2: «Los
     * usuarios sancionados deben tener derecho a apelar»). La presenta
     * ms-identidad en su nombre despues de comprobar su contrasena, y aqui se
     * aplican las mismas reglas que a cualquier apelacion ({@link #apelar}),
     * solo sobre la sancion que le impide entrar: por esta via no se apela
     * nada mas.
     */
    @Transactional
    public Apelacion apelarSinSesion(UUID usuarioId, UUID sancionId, String argumento) {
        Sancion activa = activaDe(Objects.requireNonNull(usuarioId)).orElseThrow(() ->
                new SancionRechazada(SancionRechazada.Motivo.APELACION_NO_PROCEDE,
                        "la cuenta ya no tiene una sancion que le impida entrar"));
        if (!activa.id().equals(sancionId)) {
            throw new SancionRechazada(SancionRechazada.Motivo.APELACION_NO_PROCEDE,
                    "sin sesion solo se apela la sancion que impide entrar");
        }
        return apelar(new Actor(usuarioId, "JUGADOR"), sancionId, argumento);
    }

    /** Que apelaciones ve el panel (SA-4): las pendientes, las resueltas o todas. */
    public enum EstadoDeLaLista { PENDIENTES, RESUELTAS, TODAS }

    /**
     * El panel de apelaciones (HU-USR-007, §7.3.2 «revision por parte de un
     * panel»): las pendientes de la mas antigua a la mas nueva, que es el
     * orden en que vencen; las resueltas, de la ultima decision a la primera.
     */
    @Transactional(readOnly = true)
    public List<Apelacion> apelacionesDelPanel(Actor actor, EstadoDeLaLista estado) {
        if (!actor.puedeModerar()) {
            throw new SancionRechazada(SancionRechazada.Motivo.PERMISO_INSUFICIENTE, "el panel es de moderacion");
        }
        return switch (estado == null ? EstadoDeLaLista.PENDIENTES : estado) {
            case PENDIENTES -> apelaciones.findByEstadoOrderByCreadaEnAsc(Apelacion.Estado.PENDIENTE);
            case RESUELTAS -> apelaciones.findByEstadoNotOrderByResueltaEnDesc(Apelacion.Estado.PENDIENTE);
            case TODAS -> apelaciones.findAllByOrderByCreadaEnDesc();
        };
    }

    /** Las pendientes, de la mas antigua a la mas nueva (como {@link #apelacionesDelPanel} sin filtro). */
    @Transactional(readOnly = true)
    public List<Apelacion> apelacionesPendientes(Actor actor) {
        return apelacionesDelPanel(actor, EstadoDeLaLista.PENDIENTES);
    }

    @Transactional(readOnly = true)
    public List<Apelacion> misApelaciones(Actor actor) {
        return apelaciones.findByUsuarioIdOrderByCreadaEnDesc(actor.id());
    }

    /** La decision del panel (HU-USR-007 CA-03/CA-04). Reducir exige la nueva fecha fin. */
    @Transactional
    public Apelacion resolver(Actor actor, UUID apelacionId, Apelacion.Estado decision, String motivo,
                              OffsetDateTime nuevaVigencia) {
        if (!actor.puedeAdministrar()) {
            throw new SancionRechazada(SancionRechazada.Motivo.PERMISO_INSUFICIENTE,
                    "las apelaciones las resuelve un administrador");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new SancionRechazada(SancionRechazada.Motivo.SOLICITUD_INVALIDA, "la decision va motivada");
        }
        Apelacion apelacion = apelaciones.findById(apelacionId).orElseThrow(() ->
                new SancionRechazada(SancionRechazada.Motivo.NO_ENCONTRADA, "no hay ninguna apelacion " + apelacionId));
        if (apelacion.estado() != Apelacion.Estado.PENDIENTE) {
            throw new SancionRechazada(SancionRechazada.Motivo.APELACION_RESUELTA, "la apelacion ya esta resuelta");
        }
        Sancion sancion = sanciones.findById(apelacion.sancionId()).orElseThrow();
        exigirJerarquia(actor, sancion.usuarioId(), "resolver la apelacion de");
        OffsetDateTime ahora = ahora();
        try {
            switch (decision) {
                case REVERTIDA -> sancion.revertir(actor.id(), motivo, ahora);
                case REDUCIDA -> {
                    if (nuevaVigencia == null) {
                        throw new IllegalArgumentException("reducir exige la nueva fecha fin");
                    }
                    sancion.reducirHasta(nuevaVigencia, ahora);
                }
                case MANTENIDA -> { /* la sancion sigue igual */ }
                default -> throw new IllegalArgumentException("decision invalida");
            }
            apelacion.resolver(decision, motivo.strip(), actor.id(), ahora,
                    decision == Apelacion.Estado.REDUCIDA ? nuevaVigencia : null);
        } catch (IllegalArgumentException | IllegalStateException invalida) {
            throw new SancionRechazada(SancionRechazada.Motivo.SOLICITUD_INVALIDA, invalida.getMessage());
        }
        sanciones.save(sancion);
        apelaciones.save(apelacion);
        BITACORA.info("Apelacion resuelta: id={} decision={} por={} sancion={}", apelacion.id(), decision,
                actor.id(), sancion.id());
        salidas.resolucion(apelacion, sancion, TextosDeSancion.tituloDeLaResolucion(decision),
                TextosDeSancion.cuerpoDeLaResolucion(sancion, decision, motivo,
                        decision == Apelacion.Estado.REDUCIDA ? nuevaVigencia : null),
                ahora);
        return apelacion;
    }

    /* ---- helpers ---- */

    /**
     * 7.3.1: solo sobre un rol estrictamente inferior. Para actuar sobre una
     * cuenta administrativa, primero un Super Administrador le cambia el rol en
     * ms-identidad (y ese cambio queda auditado).
     */
    private void exigirJerarquia(Actor actor, UUID cuenta, String que) {
        String rolDeLaCuenta = roles.rolDe(cuenta);
        if (!actor.superaA(rolDeLaCuenta)) {
            BITACORA.info("Sancion rechazada por jerarquia: actor={} ({}) cuenta={} ({})", actor.id(), actor.rol(),
                    cuenta, rolDeLaCuenta);
            throw new SancionRechazada(SancionRechazada.Motivo.JERARQUIA_INSUFICIENTE,
                    "no puedes " + que + " una cuenta con un rol igual o superior al tuyo");
        }
    }

    private boolean estaBaneado(UUID usuarioId, OffsetDateTime ahora) {
        return sanciones.findByUsuarioIdAndRevertidaEnIsNullAndTipoNot(usuarioId, Sancion.Tipo.ADVERTENCIA).stream()
                .anyMatch(s -> s.tipo() == Sancion.Tipo.BANEO && s.restringeEn(ahora));
    }

    private Duration duracionValida(Long horas) {
        if (horas == null) {
            throw new SancionRechazada(SancionRechazada.Motivo.SOLICITUD_INVALIDA,
                    "la suspension lleva su duracion en horas");
        }
        Duration duracion = Duration.ofHours(horas);
        Duration minima = limites.suspensionMinima();
        Duration maxima = limites.suspensionMaxima();
        if (duracion.compareTo(minima) < 0 || duracion.compareTo(maxima) > 0) {
            throw new SancionRechazada(SancionRechazada.Motivo.SOLICITUD_INVALIDA,
                    "la suspension va de " + minima.toHours() + " horas a " + maxima.toDays() + " dias");
        }
        return duracion;
    }

    static String tituloDe(Sancion sancion) {
        return TextosDeSancion.tituloDeLaEmision(sancion);
    }

    /**
     * El aviso que le llega al jugador.
     *
     * <p><b>El plazo sale del mismo sitio que la validacion.</b> Hasta aqui
     * esta frase decia «30 dias» escrito a mano mientras {@link #apelar}
     * rechazaba comparando contra {@code limites.plazoDeApelacion()}, que es
     * configurable desde admin-parametros. Ese contraste —la validacion leia el
     * parametro y el aviso no— <b>era</b> el defecto: bastaba que el Product
     * Owner bajara {@code sanciones.apelacion.plazo-dias} a 7 para que el
     * sistema le prometiera al sancionado treinta dias de plazo y despues le
     * rechazara la apelacion al octavo, sin que nada en el codigo lo delatara.
     *
     * <p>Desde SA-4 dice la fecha (no «N dias»), en la hora de Colombia, con la
     * causal y la politica (§7.3.7).
     */
    String cuerpoDe(Sancion sancion) {
        return TextosDeSancion.cuerpoDeLaEmision(sancion, sancion.emitidaEn().plus(limites.plazoDeApelacion()));
    }

    private static String vacioANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }

    /**
     * Metricas de moderacion de un periodo (HU-MET-001): solo agregados.
     * Un periodo vacio no es error: devuelve ceros (la ausencia de actividad
     * tambien es evidencia).
     */
    @Transactional(readOnly = true)
    public MetricasDeModeracion metricas(OffsetDateTime desde, OffsetDateTime hasta) {
        if (desde == null || hasta == null || !hasta.isAfter(desde)) {
            throw new SancionRechazada(SancionRechazada.Motivo.SOLICITUD_INVALIDA,
                    "el periodo necesita desde y hasta, con hasta posterior a desde");
        }
        return MetricasDeModeracion.de(desde, hasta,
                sanciones.findByEmitidaEnBetweenOrderByEmitidaEnAsc(desde, hasta),
                apelaciones.findByCreadaEnBetween(desde, hasta));
    }

    private OffsetDateTime ahora() {
        return OffsetDateTime.now(reloj).withOffsetSameInstant(ZoneOffset.UTC);
    }
}
