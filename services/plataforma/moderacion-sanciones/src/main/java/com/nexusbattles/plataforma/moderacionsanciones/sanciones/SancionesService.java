package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    @org.springframework.beans.factory.annotation.Autowired
    public SancionesService(SancionRepository sanciones, ApelacionRepository apelaciones,
                            SalidasDeSancion salidas, Clock reloj, LimitesDeSancion limites) {
        this.sanciones = Objects.requireNonNull(sanciones);
        this.apelaciones = Objects.requireNonNull(apelaciones);
        this.salidas = Objects.requireNonNull(salidas);
        this.reloj = Objects.requireNonNull(reloj);
        this.limites = Objects.requireNonNull(limites);
    }

    /** Limites fijos (pruebas): rango de la suspension y plazo de apelacion de 30 dias. */
    public SancionesService(SancionRepository sanciones, ApelacionRepository apelaciones,
                            SalidasDeSancion salidas, Clock reloj, long minimaHoras, long maximaDias) {
        this(sanciones, apelaciones, salidas, reloj, LimitesDeSancion.Fijos.de(minimaHoras, maximaDias, 30));
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

    /** Lo que se pide al emitir. {@code confirmacion} solo se mira en el baneo (CA-01 de HU-USR-006). */
    public record SolicitudDeSancion(UUID usuarioId, Sancion.Tipo tipo, String motivo, String politica,
                                     String comentarioId, Long duracionHoras, boolean confirmacion) {
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

        Sancion sancion = new Sancion(UUID.randomUUID(), solicitud.usuarioId(), solicitud.tipo(),
                solicitud.motivo().strip(), vacioANulo(solicitud.politica()), vacioANulo(solicitud.comentarioId()),
                actor.id(), actor.rol(), ahora, vigenteHasta);
        sanciones.save(sancion);
        BITACORA.info("Sancion emitida: id={} tipo={} usuario={} por={} ({}) vigenteHasta={}",
                sancion.id(), sancion.tipo(), sancion.usuarioId(), actor.id(), actor.rol(), vigenteHasta);
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
        sancion.revertir(actor.id(), motivoLimpio, ahora);
        sanciones.save(sancion);
        BITACORA.info("Sancion levantada: id={} tipo={} usuario={} por={} ({})", sancion.id(), sancion.tipo(),
                sancion.usuarioId(), actor.id(), actor.rol());
        salidas.levantamiento(sancion, "Tu " + nombreDe(sancion.tipo()) + " fue levantada",
                "Un administrador levanto tu " + nombreDe(sancion.tipo()) + ". Motivo: " + motivoLimpio + ".", ahora);
        return sancion;
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
        return apelacion;
    }

    @Transactional(readOnly = true)
    public List<Apelacion> apelacionesPendientes(Actor actor) {
        if (!actor.puedeModerar()) {
            throw new SancionRechazada(SancionRechazada.Motivo.PERMISO_INSUFICIENTE, "el panel es de moderacion");
        }
        return apelaciones.findByEstadoOrderByCreadaEnAsc(Apelacion.Estado.PENDIENTE);
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
        salidas.resolucion(apelacion, sancion, "Tu apelacion fue " + decision.name().toLowerCase(),
                "Decision del panel sobre tu " + sancion.tipo().name().toLowerCase() + ": " + motivo.strip()
                        + (decision == Apelacion.Estado.REDUCIDA ? " Nueva fecha fin: " + nuevaVigencia + "." : ""),
                ahora);
        return apelacion;
    }

    /* ---- helpers ---- */

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

    private static String nombreDe(Sancion.Tipo tipo) {
        return switch (tipo) {
            case ADVERTENCIA -> "advertencia";
            case SUSPENSION -> "suspension";
            case BANEO -> "inhabilitacion";
        };
    }

    static String tituloDe(Sancion sancion) {
        return switch (sancion.tipo()) {
            case ADVERTENCIA -> "Has recibido una advertencia";
            case SUSPENSION -> "Tu cuenta esta suspendida hasta " + sancion.vigenteHasta();
            case BANEO -> "Tu cuenta ha sido inhabilitada de forma definitiva";
        };
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
     * <p>Por eso este metodo dejo de ser estatico: necesita los limites
     * vigentes, que son estado del servicio y no una constante.
     */
    String cuerpoDe(Sancion sancion) {
        StringBuilder cuerpo = new StringBuilder("Motivo: ").append(sancion.motivo()).append('.');
        if (sancion.politica() != null) {
            cuerpo.append(" Politica: ").append(sancion.politica()).append('.');
        }
        cuerpo.append(" Puedes apelar dentro de los ").append(limites.plazoDeApelacion().toDays())
                .append(" dias siguientes desde Mi cuenta > Sanciones.");
        return cuerpo.toString();
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

    /**
     * Usuarios con {@code minimo} sanciones no revertidas o mas: una de las dos
     * senales de «comportamiento sospechoso» que definio el PO para HU-USR-008
     * (D-45). Solo quien modera: es una lista con identificadores de personas.
     * La lista se corta en {@link #MAXIMO_DE_REINCIDENTES}; {@code total} dice
     * cuantos hay en realidad.
     */
    @Transactional(readOnly = true)
    public Reincidentes reincidentes(Actor actor, int minimo) {
        if (!actor.puedeModerar()) {
            throw new SancionRechazada(SancionRechazada.Motivo.PERMISO_INSUFICIENTE,
                    "la lista de reincidentes es de quien modera");
        }
        if (minimo < 1) {
            throw new SancionRechazada(SancionRechazada.Motivo.SOLICITUD_INVALIDA,
                    "el minimo de sanciones tiene que ser 1 o mas");
        }
        List<UsuarioReincidente> usuarios = sanciones
                .reincidentes(minimo, org.springframework.data.domain.PageRequest.of(0, MAXIMO_DE_REINCIDENTES))
                .stream()
                .map(r -> new UsuarioReincidente(r.getUsuarioId(), r.getSanciones(), r.getUltimaEn()))
                .toList();
        return new Reincidentes(minimo, sanciones.contarReincidentes(minimo), usuarios);
    }

    /** Cuantos usuarios devuelve como mucho la lista de reincidentes. */
    static final int MAXIMO_DE_REINCIDENTES = 100;

    public record Reincidentes(int minimo, long total, List<UsuarioReincidente> usuarios) {
    }

    public record UsuarioReincidente(UUID usuarioId, long sanciones, OffsetDateTime ultimaEn) {
    }

    private OffsetDateTime ahora() {
        return OffsetDateTime.now(reloj).withOffsetSameInstant(ZoneOffset.UTC);
    }
}
