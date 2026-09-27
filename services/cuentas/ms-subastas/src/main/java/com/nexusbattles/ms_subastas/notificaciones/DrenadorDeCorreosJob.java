package com.nexusbattles.ms_subastas.notificaciones;

import com.nexusbattles.ms_subastas.seguridad.Traza;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Entrega por correo ({@code POST /correos/subasta}) los avisos del outbox que
 * lo piden (B8, 7.7.8): nueva puja al vendedor, puja superada, victoria,
 * cierre por vencimiento, cancelacion, recordatorio y vencimiento de un
 * pendiente.
 *
 * <p>Tres pasos por aviso, sin guardar nada del jugador: el correo y el apodo
 * los da ms-identidad en el momento ({@code GET /internal/usuarios/{uid}/contacto}),
 * y el envio lleva como {@code Idempotency-Key} el id del aviso, que es estable
 * por evento: un reintento nunca manda dos correos.
 *
 * <p><b>Reintentos con espera creciente y final.</b> Si identidad o correo no
 * responden, el aviso se reintenta con espera exponencial
 * ({@code app.correo.reintento-base-segundos}, doblando hasta una hora) y el
 * lote se corta: si no responden para uno, no van a responder para el
 * siguiente. Tras {@code app.correo.max-intentos}, FALLIDO y a la bitacora.
 * Nada se reintenta para siempre, que es lo que pasaba con los avisos de la
 * bandeja antes de B8.
 *
 * <p>No se escribe a quien no tiene correo o esta BANEADO: DESCARTADO. Un 400
 * de correo es un fallo de forma: FALLIDO. Un 404 del contacto NO descarta: la
 * ruta de ms-identidad esta pendiente de desplegar (B2) y descartar por eso
 * tiraria todos los correos en silencio; se reintenta hasta FALLIDO.
 */
@Component
public class DrenadorDeCorreosJob {

    private static final Logger log = LoggerFactory.getLogger(DrenadorDeCorreosJob.class);
    private static final Duration ESPERA_MAXIMA = Duration.ofHours(1);

    private final NotificacionPendienteRepository repositorio;
    private final ObjectProvider<CorreoSubastaClient> correo;
    private final ObjectProvider<ContactoClient> contactos;
    private final Clock clock;
    private final int lote;
    private final Duration esperaBase;
    private final int maxIntentos;

    public DrenadorDeCorreosJob(NotificacionPendienteRepository repositorio,
                                ObjectProvider<CorreoSubastaClient> correo,
                                ObjectProvider<ContactoClient> contactos,
                                Clock clock,
                                @Value("${app.correo.lote:50}") int lote,
                                @Value("${app.correo.reintento-base-segundos:30}") long esperaBaseSegundos,
                                @Value("${app.correo.max-intentos:8}") int maxIntentos) {
        this.repositorio = repositorio;
        this.correo = correo;
        this.contactos = contactos;
        this.clock = clock;
        this.lote = lote;
        this.esperaBase = Duration.ofSeconds(esperaBaseSegundos);
        this.maxIntentos = maxIntentos;
    }

    @Scheduled(fixedDelayString = "${app.correo.drenaje-intervalo-ms:10000}")
    public void drenar() {
        CorreoSubastaClient cliente = correo.getIfAvailable();
        ContactoClient identidad = contactos.getIfAvailable();
        if (cliente == null || identidad == null) {
            return;
        }
        List<NotificacionPendiente> pendientes = repositorio.correosPorEnviar(clock.instant(), PageRequest.of(0, lote));
        if (pendientes.isEmpty()) {
            return;
        }
        boolean trazaPropia = Traza.abrir();
        try {
            for (NotificacionPendiente aviso : pendientes) {
                boolean seguir = entregar(aviso, cliente, identidad);
                repositorio.save(aviso);
                if (!seguir) {
                    break;
                }
            }
        } finally {
            Traza.cerrar(trazaPropia);
        }
    }

    /** @return si se puede seguir con el lote (falso: identidad o correo no responden) */
    private boolean entregar(NotificacionPendiente aviso, CorreoSubastaClient cliente, ContactoClient identidad) {
        Instant ahora = clock.instant();
        try {
            Optional<ContactoClient.Contacto> contacto = identidad.contactoDe(aviso.getDestinatarioId());
            if (contacto.isEmpty() || contacto.get().email() == null || contacto.get().email().isBlank()
                    || "BANEADO".equalsIgnoreCase(contacto.get().estado())) {
                aviso.setCorreoEstado(EstadoCorreo.DESCARTADO);
                log.info("Correo del aviso {} descartado: no hay a quien escribirle", aviso.getId());
                return true;
            }
            ContactoClient.Contacto destinatario = contacto.get();
            cliente.enviar(claveDe(aviso), new CorreoSubastaClient.CorreoSubasta(destinatario.email(),
                    apodoDe(destinatario), asuntoDe(aviso), aviso.getDetalle() == null ? aviso.tituloVisible()
                            : aviso.getDetalle(), true));
            aviso.setCorreoEstado(EstadoCorreo.ENVIADO);
            aviso.setCorreoEnviadoEn(ahora);
            return true;
        } catch (CorreoRechazadoException rechazo) {
            aviso.setCorreoEstado(EstadoCorreo.FALLIDO);
            log.error("Correo del aviso {} rechazado para siempre: {}", aviso.getId(), rechazo.getMessage());
            return true;
        } catch (CorreoNoDisponibleException | IllegalStateException noResponde) {
            int intentos = aviso.getCorreoIntentos() + 1;
            aviso.setCorreoIntentos(intentos);
            if (intentos >= maxIntentos) {
                aviso.setCorreoEstado(EstadoCorreo.FALLIDO);
                log.error("Correo del aviso {} FALLIDO tras {} intentos: {}", aviso.getId(), intentos,
                        noResponde.getMessage());
            } else {
                aviso.setCorreoProximoIntentoEn(ahora.plus(espera(intentos)));
                log.warn("Correo del aviso {} no salio (intento {} de {}): {}. Se reintenta.", aviso.getId(), intentos,
                        maxIntentos, noResponde.getMessage());
            }
            return false;
        }
    }

    /** base, 2 x base, 4 x base... hasta una hora. */
    Duration espera(int intentos) {
        Duration espera = esperaBase.multipliedBy(1L << Math.min(intentos - 1, 16));
        return espera.compareTo(ESPERA_MAXIMA) > 0 ? ESPERA_MAXIMA : espera;
    }

    /** Estable por evento: el id del aviso se deriva de la clave del evento. */
    static String claveDe(NotificacionPendiente aviso) {
        return "ms-subastas-" + aviso.getId();
    }

    private static String asuntoDe(NotificacionPendiente aviso) {
        return aviso.getAsunto() == null || aviso.getAsunto().isBlank() ? aviso.tituloVisible() : aviso.getAsunto();
    }

    private static String apodoDe(ContactoClient.Contacto contacto) {
        return contacto.apodo() == null || contacto.apodo().isBlank() ? "jugador" : contacto.apodo();
    }
}
