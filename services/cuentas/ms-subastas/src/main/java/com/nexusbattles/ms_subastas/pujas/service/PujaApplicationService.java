package com.nexusbattles.ms_subastas.pujas.service;

import com.nexusbattles.ms_subastas.notificaciones.AvisosDeSubasta;
import com.nexusbattles.ms_subastas.panel.model.PendienteDeRecoger;
import com.nexusbattles.ms_subastas.panel.repository.PendienteDeRecogerRepository;
import com.nexusbattles.ms_subastas.pujas.model.EstadoPuja;
import com.nexusbattles.ms_subastas.pujas.model.Puja;
import com.nexusbattles.ms_subastas.pujas.model.TipoPuja;
import com.nexusbattles.ms_subastas.pujas.repository.PujaAutomaticaRepository;
import com.nexusbattles.ms_subastas.pujas.repository.PujaRepository;
import com.nexusbattles.ms_subastas.reglas.FuenteDeReglas;
import com.nexusbattles.ms_subastas.reglas.ReglasDelDocumento;
import com.nexusbattles.ms_subastas.subastas.model.EstadoSubasta;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import com.nexusbattles.ms_subastas.subastas.realtime.SubastaActualizadaEvent;
import com.nexusbattles.ms_subastas.subastas.repository.SubastaRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Orquesta una puja: abre la transaccion, toma el lock pesimista de la
 * subasta, arma el ContextoParticipacion desde la base de datos y delega las
 * reglas de negocio en MotorPujasService.
 *
 * El lock se toma ANTES de leer la puja vigente y se suelta al cerrar la
 * transaccion, de modo que "leer oferta vigente -> validar -> escribir nueva
 * puja" es atomico frente a otras pujas sobre la misma subasta. Esa es la
 * garantia que exige el caso de prueba de concurrencia de HU-SUB-004.
 *
 * Ojo con la latencia: la reserva de creditos en ms-finanzas ocurre dentro de
 * este lock, asi que el tiempo de respuesta de ese servicio serializa todas
 * las pujas de la subasta. Es el numero que hay que acordar con Juan Diego.
 *
 * <p><b>B8.</b> Cada hecho encola sus avisos (7.7.8) en la misma transaccion
 * por {@link AvisosDeSubasta}, y el cierre con ganador deja el producto
 * pendiente de recoger (7.7.9).
 */
@Service
public class PujaApplicationService {

    private final SubastaRepository subastaRepository;
    private final PujaRepository pujaRepository;
    private final MotorPujasService motorPujas;
    private final AvisosDeSubasta avisos;
    private final PujaAutomaticaRepository automaticas;
    private final PendienteDeRecogerRepository pendientes;
    private final FuenteDeReglas reglas;
    private final Clock clock;

    /**
     * Alimenta el canal en vivo (HU-SUB-011 y, desde B8, el canal por subasta).
     * Se publica desde aqui y no desde MotorPujasService: el motor no conoce
     * persistencia ni Spring —ArchUnit lo verifica— y no sabe cuando una puja
     * quedo guardada.
     *
     * <p>Quien traduce el evento a STOMP es SubastaRealtimePublisher, de modo
     * que aqui no se conoce ni SimpMessagingTemplate ni el nombre del canal.
     */
    private final ApplicationEventPublisher eventos;

    public PujaApplicationService(SubastaRepository subastaRepository, PujaRepository pujaRepository,
                                  MotorPujasService motorPujas, AvisosDeSubasta avisos,
                                  PujaAutomaticaRepository automaticas, PendienteDeRecogerRepository pendientes,
                                  FuenteDeReglas reglas, Clock clock, ApplicationEventPublisher eventos) {
        this.subastaRepository = Objects.requireNonNull(subastaRepository);
        this.pujaRepository = Objects.requireNonNull(pujaRepository);
        this.motorPujas = Objects.requireNonNull(motorPujas);
        this.avisos = Objects.requireNonNull(avisos);
        this.automaticas = Objects.requireNonNull(automaticas);
        this.pendientes = Objects.requireNonNull(pendientes);
        this.reglas = Objects.requireNonNull(reglas);
        this.clock = Objects.requireNonNull(clock);
        this.eventos = Objects.requireNonNull(eventos);
    }

    @Transactional
    public Puja pujar(UUID subastaId, UUID jugadorId, BigDecimal monto, String idempotencyKey) {
        return pujar(subastaId, jugadorId, null, monto, idempotencyKey);
    }

    /**
     * @param apodo el del token (claim {@code sub}), para que el vendedor sepa
     *              quien pujo —anonimizado—. Nunca identifica a nadie: eso lo
     *              hace {@code jugadorId}.
     */
    @Transactional
    public Puja pujar(UUID subastaId, UUID jugadorId, String apodo, BigDecimal monto, String idempotencyKey) {
        return registrar(subastaId, jugadorId, apodo, monto, idempotencyKey, TipoPuja.MANUAL);
    }

    /**
     * Misma orquestacion que pujar, pero la puja queda marcada como AUTOMATICA.
     * La usa el motor de pujas automaticas, que no es una peticion del jugador
     * aunque pase por las mismas reglas y el mismo lock.
     */
    @Transactional
    public Puja pujarAutomaticamente(UUID subastaId, UUID jugadorId, BigDecimal monto, String idempotencyKey) {
        String apodo = automaticas.findBySubastaIdAndJugadorId(subastaId, jugadorId)
                .map(automatica -> automatica.getApodoJugador())
                .orElse(null);
        return registrar(subastaId, jugadorId, apodo, monto, idempotencyKey, TipoPuja.AUTOMATICA);
    }

    private Puja registrar(UUID subastaId, UUID jugadorId, String apodo, BigDecimal monto, String idempotencyKey,
                           TipoPuja tipo) {
        // Las reglas vigentes se leen de admin-parametros con cache corta. Se
        // leen ANTES del lock para que, si la cache caduco, la peticion al
        // catalogo no ocurra con las pujas de esta subasta esperando detras.
        reglas.vigentes();

        Subasta subasta = cargarConLock(subastaId);

        Puja yaRegistrada = reproduccionDe(idempotencyKey, subastaId, jugadorId);
        if (yaRegistrada != null) {
            return yaRegistrada;
        }

        Puja pujaVigente = pujaRepository.findBySubastaIdAndEstado(subastaId, EstadoPuja.ACTIVA).orElse(null);

        Puja nuevaPuja = motorPujas.pujar(subasta, pujaVigente, jugadorId, monto, contextoDe(jugadorId, subastaId), idempotencyKey, tipo);
        nuevaPuja.setApodoPostor(apodo);

        // saveAndFlush, no save: el indice unico parcial solo admite una puja
        // ACTIVA por subasta, e Hibernate ordena los INSERT antes que los
        // UPDATE al volcar la sesion. Sin este flush explicito, la nueva puja
        // se insertaria antes de que la anterior pase a SUPERADA y la
        // constraint la rechazaria.
        if (pujaVigente != null) {
            pujaRepository.saveAndFlush(pujaVigente);
        }
        subastaRepository.save(subasta);
        Puja persistida = pujaRepository.save(nuevaPuja);

        // Misma transaccion que la puja: si el aviso no se escribe, la puja
        // tampoco. Al vendedor (nueva puja), al superado y a quien la sigue.
        avisos.pujaRegistrada(subasta, persistida, pujaVigente);

        // Solo si la puja quedo registrada: una rechazada no cambia el listado,
        // y avisar de todas formas llenaria el canal de ruido —perder la carrera
        // contra otro postor es el caso normal, no una excepcion.
        eventos.publishEvent(new SubastaActualizadaEvent(this, subasta));
        return persistida;
    }

    @Transactional
    public Puja comprarAhora(UUID subastaId, UUID jugadorId, String idempotencyKey) {
        return comprarAhora(subastaId, jugadorId, null, idempotencyKey);
    }

    @Transactional
    public Puja comprarAhora(UUID subastaId, UUID jugadorId, String apodo, String idempotencyKey) {
        Subasta subasta = cargarConLock(subastaId);

        Puja yaComprada = reproduccionDe(idempotencyKey, subastaId, jugadorId);
        if (yaComprada != null) {
            return yaComprada;
        }

        Puja pujaVigente = pujaRepository.findBySubastaIdAndEstado(subastaId, EstadoPuja.ACTIVA).orElse(null);

        // Se consulta ANTES de cerrar: despues del cierre la lista es la misma,
        // pero leerla aqui deja claro que el aviso va a todos los que pujaron,
        // no solo a quien iba ganando.
        List<UUID> postores = pujaRepository.findDistinctJugadorIdBySubastaId(subastaId);

        Puja pujaGanadora = motorPujas.comprarAhora(subasta, pujaVigente, jugadorId, idempotencyKey);
        pujaGanadora.setApodoPostor(apodo);

        // Mismo motivo que en pujar(): la puja vigente debe dejar de ser ACTIVA
        // en la base de datos antes de insertar la ganadora.
        if (pujaVigente != null) {
            pujaRepository.saveAndFlush(pujaVigente);
        }
        subastaRepository.save(subasta);
        Puja persistida = pujaRepository.save(pujaGanadora);

        // Misma transaccion que el cierre: si esto no se escribe, el cierre
        // tampoco, y no se puede perder un aviso. Los avisos leen quien tenia
        // automatica ANTES de desactivarlas.
        avisos.compraInmediata(subasta, persistida, postores);
        automaticas.desactivarTodas(subastaId);

        // La subasta desaparece del listado en vivo al cerrarse, asi que el
        // canal tiene que enterarse igual que de una puja.
        eventos.publishEvent(new SubastaActualizadaEvent(this, subasta));
        return persistida;
    }

    /**
     * Devuelve la puja que ya creo una peticion anterior con esta misma
     * Idempotency-Key, o {@code null} si la clave es nueva.
     *
     * <p>Se consulta DESPUES de tomar el lock pesimista de la subasta, y ese
     * orden es lo que hace segura la reproduccion: dos reintentos simultaneos
     * se serializan en el lock, asi que el segundo ya ve la puja que escribio
     * el primero. Consultarlo antes del lock dejaria pasar a los dos y la
     * segunda insercion moriria contra el unico de la base de datos.
     *
     * <p>Si la clave existe pero apunta a otra subasta o a otro jugador, no se
     * reproduce nada: devolverle a alguien una puja que no es suya seria peor
     * que rechazar la peticion.
     */
    private Puja reproduccionDe(String idempotencyKey, UUID subastaId, UUID jugadorId) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        Puja anterior = pujaRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (anterior == null) {
            return null;
        }
        if (!anterior.getSubastaId().equals(subastaId) || !anterior.getJugadorId().equals(jugadorId)) {
            throw new PujaRechazadaException(PujaRechazadaException.Motivo.CLAVE_REUTILIZADA,
                    "La clave de idempotencia ya se uso en otra subasta o para otro jugador");
        }
        return anterior;
    }

    /**
     * Cierra una subasta vencida (7.7.7). Con ganador: cobra, transfiere y deja
     * el producto pendiente de recoger 7 dias (7.7.9). Sin ofertas: el producto
     * vuelve al vendedor y la comision no se reembolsa.
     */
    @Transactional
    public void cerrarPorVencimiento(UUID subastaId) {
        Subasta subasta = cargarConLock(subastaId);
        Puja pujaVigente = pujaRepository.findBySubastaIdAndEstado(subastaId, EstadoPuja.ACTIVA).orElse(null);

        motorPujas.cerrarPorVencimiento(subasta, pujaVigente);

        if (pujaVigente != null) {
            pujaRepository.save(pujaVigente);
        }
        subastaRepository.save(subasta);

        if (subasta.getEstado() == EstadoSubasta.ADJUDICADA && pujaVigente != null) {
            Instant ganadaEn = clock.instant();
            PendienteDeRecoger pendiente = pendientes.save(new PendienteDeRecoger(subasta.getId(),
                    pujaVigente.getJugadorId(), subasta.getElementoInventarioId(), pujaVigente.getMonto(),
                    ganadaEn, ganadaEn.plus(ReglasDelDocumento.PLAZO_PARA_RECOGER)));
            avisos.cerradaConGanador(subasta, pujaVigente, pendiente);
        } else {
            avisos.cerradaSinOfertas(subasta);
        }
        automaticas.desactivarTodas(subastaId);

        // La subasta cambio de estado (ADJUDICADA o SIN_ADJUDICACION), asi que
        // el listado en vivo tiene que enterarse igual que con una puja o compra.
        eventos.publishEvent(new SubastaActualizadaEvent(this, subasta));
    }

    private Subasta cargarConLock(UUID subastaId) {
        return subastaRepository.findByIdParaActualizar(subastaId)
                .orElseThrow(() -> new SubastaNoEncontradaException(subastaId));
    }

    private ContextoParticipacion contextoDe(UUID jugadorId, UUID subastaId) {
        return new ContextoParticipacion(
                pujaRepository.findFirstByJugadorIdAndSubastaIdOrderByCreadaEnDesc(jugadorId, subastaId)
                        .map(Puja::getCreadaEn).orElse(null),
                pujaRepository.countByJugadorIdAndEstado(jugadorId, EstadoPuja.ACTIVA));
    }
}
