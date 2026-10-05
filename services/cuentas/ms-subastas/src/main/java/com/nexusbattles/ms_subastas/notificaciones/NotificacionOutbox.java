package com.nexusbattles.ms_subastas.notificaciones;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Encola los avisos de subastas en la base de datos de este servicio, dentro
 * de la misma transaccion que el cambio de negocio que los motiva.
 *
 * <p>Patron outbox transaccional: la intencion del aviso se escribe junto con
 * el cambio, asi que un aviso no se pierde si el proceso muere justo despues
 * de cerrar una subasta; {@link DrenadorDeNotificacionesJob} lo entrega a la
 * bandeja y {@link DrenadorDeCorreosJob} al correo.
 *
 * <p><b>Idempotente por evento (B8).</b> El id de la fila se deriva de
 * «tipo : subasta : destinatario : discriminante» (UUID v3), y encolar es un
 * {@code INSERT ... ON CONFLICT DO NOTHING}. El mismo hecho encolado dos veces
 * —un trabajo programado que repite una pasada, un reintento— es un solo
 * aviso, y el mismo id sirve de identificador ante notificaciones (409 = ya
 * entregado) y de {@code Idempotency-Key} ante correo.
 */
@Service
public class NotificacionOutbox {

    private final NotificacionPendienteRepository repositorio;
    private final Clock clock;
    private final boolean correoHabilitado;

    /** Sin salida de correo: las pruebas y los entornos sin {@code CORREO_URL}. */
    public NotificacionOutbox(NotificacionPendienteRepository repositorio, Clock clock) {
        this(repositorio, clock, false);
    }

    @Autowired
    public NotificacionOutbox(NotificacionPendienteRepository repositorio, Clock clock,
                              ObjectProvider<CorreoSubastaClient> correo) {
        this(repositorio, clock, correo.getIfAvailable() != null);
    }

    NotificacionOutbox(NotificacionPendienteRepository repositorio, Clock clock, boolean correoHabilitado) {
        this.repositorio = Objects.requireNonNull(repositorio, "repositorio");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.correoHabilitado = correoHabilitado;
    }

    /**
     * Encola un aviso si ese evento no estaba ya encolado.
     *
     * @param discriminante lo que distingue este hecho de otro del mismo tipo en
     *                      la misma subasta y para el mismo jugador (la puja,
     *                      «cierre», «cancelacion»...). Tiene que ser estable
     *                      entre reintentos: nunca la hora.
     * @param titulo        lo que se lee en la bandeja y el asunto del correo;
     *                      nulo = el titulo del tipo
     * @return si se encolo (falso: ya estaba)
     */
    public boolean encolar(TipoNotificacion tipo, UUID destinatario, UUID subastaId, String discriminante,
                           String titulo, String cuerpo) {
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(destinatario, "destinatario");
        Objects.requireNonNull(subastaId, "subastaId");
        String tituloFinal = titulo == null || titulo.isBlank() ? tipo.getTitulo() : titulo;
        boolean conCorreo = correoHabilitado && tipo.conCorreo();
        int filas = repositorio.encolarSiNoExiste(
                idDelEvento(tipo, subastaId, destinatario, discriminante),
                tipo.name(), destinatario, subastaId, tituloFinal, cuerpo == null ? "" : cuerpo,
                clock.instant(), conCorreo,
                conCorreo ? tituloFinal : "",
                conCorreo ? EstadoCorreo.PENDIENTE.name() : "");
        return filas > 0;
    }

    /** Identificador estable de un evento: el mismo hecho, el mismo id. */
    public static UUID idDelEvento(TipoNotificacion tipo, UUID subastaId, UUID destinatario, String discriminante) {
        String clave = "ms-subastas:" + tipo.name() + ":" + subastaId + ":" + destinatario + ":"
                + (discriminante == null ? "" : discriminante);
        return UUID.nameUUIDFromBytes(clave.getBytes(StandardCharsets.UTF_8));
    }

    /** Si los avisos que lo piden salen tambien por correo. */
    public boolean correoHabilitado() {
        return correoHabilitado;
    }

    // --- los avisos de HU-SUB-004 (criterios 2 y 4) ---------------------------

    /**
     * @param postores todos los jugadores que pujaron en la subasta
     * @param comprador quien ejecuto la compra inmediata; se excluye porque ya
     *                  recibe el resultado de su propia peticion
     */
    public void avisarCierrePorCompraInmediata(UUID subastaId, List<UUID> postores, UUID comprador) {
        postores.stream()
                .filter(postor -> !postor.equals(comprador))
                .distinct()
                .forEach(postor -> encolar(TipoNotificacion.SUBASTA_CERRADA_POR_COMPRA_INMEDIATA, postor, subastaId,
                        "compra-inmediata", null,
                        "La subasta se cerró porque otro jugador la compró de forma inmediata. "
                                + "Tus créditos retenidos ya se liberaron."));
    }

    /**
     * Se detuvo una puja automatica. El discriminante lleva el instante: la
     * misma automatica puede reactivarse (el jugador sube el limite) y volver a
     * detenerse, y eso es otro aviso. La pasada que la detiene la desactiva
     * antes de avisar, asi que no se repite sola.
     */
    public void avisarLimiteAutomaticoAlcanzado(UUID subastaId, UUID jugadorId, BigDecimal limite) {
        encolar(TipoNotificacion.LIMITE_AUTOMATICO_ALCANZADO, jugadorId, subastaId,
                "limite:" + limite.toPlainString() + ":" + clock.millis(), null,
                "Tu puja automática se detuvo: la siguiente oferta superaría tu límite de "
                        + limite.toPlainString() + " créditos.");
    }

    public void avisarAutomaticaSinSaldo(UUID subastaId, UUID jugadorId) {
        encolar(TipoNotificacion.AUTOMATICA_SIN_SALDO, jugadorId, subastaId, "sin-saldo:" + clock.millis(), null,
                "Tu puja automática se detuvo: tu saldo disponible no alcanza para cubrir la siguiente oferta.");
    }
}
