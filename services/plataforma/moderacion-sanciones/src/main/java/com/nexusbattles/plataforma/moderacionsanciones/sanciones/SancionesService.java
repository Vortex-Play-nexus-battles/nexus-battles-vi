package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
 * <p><b>Lo que no decide este servicio:</b> el estado de la cuenta en
 * ms-identidad (rechazar el inicio de sesion, cerrar sesiones) es de Cuentas
 * (HU-AUT-004 #50); aqui queda el historial, la vigencia y la consulta
 * {@code sancionActiva} que ya usan chat, comentarios y subastas. Tampoco
 * escala advertencias a suspension (CA-05 de HU-USR-004, decision del PO).
 *
 * <p>Cada emision y cada resolucion dejan un aviso para el jugador
 * (HU-NOT-005) en la tabla de avisos pendientes; {@link EntregadorDeAvisos}
 * lo entrega y reintenta.
 */
@Service
public class SancionesService {

    private static final Logger BITACORA = LoggerFactory.getLogger(SancionesService.class);

    /** Plazo para apelar desde la emision (HU-USR-007, historia). */
    static final Duration PLAZO_DE_APELACION = Duration.ofDays(30);

    private final SancionRepository sanciones;
    private final ApelacionRepository apelaciones;
    private final AvisoPendienteRepository avisos;
    private final Clock reloj;
    private final Duration suspensionMinima;
    private final Duration suspensionMaxima;

    public SancionesService(SancionRepository sanciones, ApelacionRepository apelaciones,
                            AvisoPendienteRepository avisos, Clock reloj,
                            @Value("${sanciones.suspension.minima-horas:1}") long minimaHoras,
                            @Value("${sanciones.suspension.maxima-dias:30}") long maximaDias) {
        this.sanciones = Objects.requireNonNull(sanciones);
        this.apelaciones = Objects.requireNonNull(apelaciones);
        this.avisos = Objects.requireNonNull(avisos);
        this.reloj = Objects.requireNonNull(reloj);
        this.suspensionMinima = Duration.ofHours(minimaHoras);
        this.suspensionMaxima = Duration.ofDays(maximaDias);
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
        avisar(sancion.usuarioId(), "SANCION_" + sancion.tipo().name(), tituloDe(sancion), cuerpoDe(sancion), ahora);
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
        if (ahora.isAfter(sancion.emitidaEn().plus(PLAZO_DE_APELACION))) {
            throw new SancionRechazada(SancionRechazada.Motivo.APELACION_NO_PROCEDE,
                    "el plazo para apelar (30 dias desde la sancion) ya paso");
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
        avisar(apelacion.usuarioId(), "APELACION_" + decision.name(),
                "Tu apelacion fue " + decision.name().toLowerCase(),
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
        if (duracion.compareTo(suspensionMinima) < 0 || duracion.compareTo(suspensionMaxima) > 0) {
            throw new SancionRechazada(SancionRechazada.Motivo.SOLICITUD_INVALIDA,
                    "la suspension va de " + suspensionMinima.toHours() + " horas a "
                            + suspensionMaxima.toDays() + " dias");
        }
        return duracion;
    }

    private void avisar(UUID usuarioId, String tipo, String titulo, String cuerpo, OffsetDateTime ahora) {
        avisos.save(new AvisoPendiente(UUID.randomUUID(), usuarioId, tipo, titulo, cuerpo, ahora));
    }

    static String tituloDe(Sancion sancion) {
        return switch (sancion.tipo()) {
            case ADVERTENCIA -> "Has recibido una advertencia";
            case SUSPENSION -> "Tu cuenta esta suspendida hasta " + sancion.vigenteHasta();
            case BANEO -> "Tu cuenta ha sido inhabilitada de forma definitiva";
        };
    }

    static String cuerpoDe(Sancion sancion) {
        StringBuilder cuerpo = new StringBuilder("Motivo: ").append(sancion.motivo()).append('.');
        if (sancion.politica() != null) {
            cuerpo.append(" Politica: ").append(sancion.politica()).append('.');
        }
        cuerpo.append(" Puedes apelar dentro de los 30 dias siguientes desde Mi cuenta > Sanciones.");
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

    private OffsetDateTime ahora() {
        return OffsetDateTime.now(reloj).withOffsetSameInstant(ZoneOffset.UTC);
    }
}
