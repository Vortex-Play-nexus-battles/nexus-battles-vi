package com.nexusbattles.plataforma.torneos.torneo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Ejecuta las {@link Operacion}es pendientes contra los otros servicios:
 * cobra y devuelve inscripciones en el libro de creditos, entrega el premio
 * (creditos por el libro, epica por inventario) y deja los avisos.
 *
 * <p><b>Nunca dentro de una transaccion.</b> Cada operacion se reclama con una
 * sentencia condicional corta ({@link OperacionRepository#reclamar}), se
 * ejecuta la llamada HTTP sin transaccion abierta, y el resultado se guarda en
 * otra transaccion corta. Una llamada lenta no retiene conexiones ni bloqueos
 * de la base.
 *
 * <p><b>Idempotente de punta a punta.</b> Lo llaman la peticion que creo las
 * operaciones (para que el cobro ocurra en el acto cuando el libro responde) y
 * la tarea programada (para lo que falto). Reclamar es atomico, asi que dos
 * ejecuciones no hacen la misma operacion a la vez; y si una muere despues de
 * cobrar pero antes de anotarlo, la que la retoma vuelve a llamar con la misma
 * reserva o la misma clave y el proveedor responde lo que ya hizo, sin cobrar
 * ni entregar dos veces.
 */
@Service
public class ProcesadorDeOperaciones {

    private static final Logger BITACORA = LoggerFactory.getLogger(ProcesadorDeOperaciones.class);

    static final Set<Operacion.Estado> POR_ATENDER = EnumSet.of(Operacion.Estado.PENDIENTE, Operacion.Estado.REINTENTABLE);
    static final Set<Operacion.Tipo> TODAS = EnumSet.allOf(Operacion.Tipo.class);
    static final int LOTE = 50;

    static final String CONCEPTO_DEVOLUCION = "devolucion-inscripcion-torneo";
    static final String CONCEPTO_PREMIO = "premio-torneo";

    private final OperacionRepository operaciones;
    private final TorneoRepository torneos;
    private final EquipoRepository equipos;
    private final LibroDeCreditos libro;
    private final EntregaDeInventario inventario;
    private final AvisosAlJugador canales;
    private final ConsultaDeSanciones sanciones;
    private final Hitos hitos;
    private final PoliticaDeReintentos politica;
    private final TransactionTemplate transaccion;
    private final Clock reloj;
    private final Duration presupuestoSincrono;

    public ProcesadorDeOperaciones(OperacionRepository operaciones, TorneoRepository torneos, EquipoRepository equipos,
                                   LibroDeCreditos libro, EntregaDeInventario inventario, AvisosAlJugador canales,
                                   ConsultaDeSanciones sanciones, Hitos hitos, PoliticaDeReintentos politica,
                                   TransactionTemplate transaccion, Clock reloj,
                                   @Value("${torneos.operaciones.presupuesto-sincrono-ms:3000}") long presupuestoMs) {
        this.operaciones = operaciones;
        this.torneos = torneos;
        this.equipos = equipos;
        this.libro = libro;
        this.inventario = inventario;
        this.canales = canales;
        this.sanciones = sanciones;
        this.hitos = hitos;
        this.politica = politica;
        this.transaccion = transaccion;
        this.reloj = reloj;
        this.presupuestoSincrono = Duration.ofMillis(presupuestoMs);
    }

    /**
     * Las operaciones de un torneo, de los tipos dados, que ya toca intentar.
     * La llama el caso de uso justo despues de confirmar su transaccion.
     *
     * <p>Con presupuesto de tiempo ({@code torneos.operaciones.presupuesto-sincrono-ms}):
     * con los proveedores sanos todo se hace dentro de la peticion (el cobro
     * o el premio se ven al instante), pero si uno tarda, la peticion no lo
     * espera operacion tras operacion: lo que falte queda para la tarea
     * programada. Importa sobre todo en la final, que puede informarla
     * salas-partidas y no debe quedarse esperando al premio.
     */
    public void procesarDelTorneo(UUID torneoId, Set<Operacion.Tipo> tipos) {
        List<UUID> ids = operaciones.porAtenderDelTorneo(torneoId, tipos, POR_ATENDER, Operacion.Estado.EN_CURSO,
                ahora(), PageRequest.of(0, LOTE));
        long limite = System.nanoTime() + presupuestoSincrono.toNanos();
        for (UUID id : ids) {
            if (System.nanoTime() > limite) {
                BITACORA.info("Presupuesto de tiempo agotado en torneo={}: lo pendiente lo hace la tarea programada",
                        torneoId);
                return;
            }
            procesar(id);
        }
    }

    /**
     * Un lote de todo lo que ya toca intentar, de cualquier torneo. La llama
     * la tarea programada.
     *
     * @return cuantas se intentaron
     */
    public int procesarPendientes() {
        List<UUID> ids = operaciones.porAtender(TODAS, POR_ATENDER, Operacion.Estado.EN_CURSO, ahora(),
                PageRequest.of(0, LOTE));
        ids.forEach(this::procesar);
        return ids.size();
    }

    /** Reclama y ejecuta una operacion; si otra ejecucion la tiene, no hace nada. */
    void procesar(UUID id) {
        OffsetDateTime ahora = ahora();
        Integer reclamadas = transaccion.execute(estado -> operaciones.reclamar(id, POR_ATENDER,
                Operacion.Estado.EN_CURSO, ahora, ahora.plus(politica.plazoDeBloqueo())));
        if (reclamadas == null || reclamadas == 0) {
            return;
        }
        Operacion operacion = operaciones.findById(id).orElse(null);
        if (operacion == null) {
            return;
        }
        try {
            switch (operacion.tipo()) {
                case COBRO_INSCRIPCION -> cobrar(operacion);
                case DEVOLUCION_INSCRIPCION -> devolver(operacion);
                case PREMIO -> premiar(operacion);
                case AVISO -> notificar(operacion);
                case CORREO -> enviarCorreo(operacion);
            }
        } catch (FalloDeIntegracion fallo) {
            fallar(operacion, fallo.getMessage(), fallo.reintentable());
        } catch (TorneoRechazado rechazo) {
            // Sanciones no disponibles al premiar: fail-closed, se reintenta.
            fallar(operacion, rechazo.motivo() + ": " + rechazo.getMessage(), true);
        } catch (RuntimeException inesperado) {
            BITACORA.error("Operacion {} ({}) fallo sin clasificar; se reintentara", operacion.clave(),
                    operacion.tipo(), inesperado);
            fallar(operacion, inesperado.getClass().getSimpleName() + ": " + inesperado.getMessage(), true);
        }
    }

    // ------------------------------------------------------------ por tipo

    private void cobrar(Operacion operacion) {
        libro.consumir(operacion.reservaId());
        terminar(operacion, op -> op.hecha("reserva " + operacion.reservaId() + " consumida", ahora()));
        BITACORA.info("Inscripcion cobrada: clave={} reserva={}", operacion.clave(), operacion.reservaId());
    }

    private void devolver(Operacion operacion) {
        String estado = libro.liberar(operacion.reservaId());
        if ("CONSUMIDA".equals(estado)) {
            // Ya se habia cobrado (por ejemplo, un inicio de la version 1.1.0 que
            // cobro y fallo a mitad): liberar no devuelve nada, asi que se acredita
            // el mismo monto con la clave de la devolucion como refId (idempotente).
            libro.acreditar(operacion.jugadorUid(), valor(operacion.monto()), operacion.clave(), CONCEPTO_DEVOLUCION);
            terminar(operacion, op -> op.hecha("reserva ya cobrada: devuelta con acreditar", ahora()));
        } else {
            terminar(operacion, op -> op.hecha("reserva " + operacion.reservaId() + " liberada", ahora()));
        }
        BITACORA.info("Inscripcion devuelta: clave={} reserva={} estadoPrevio={}", operacion.clave(),
                operacion.reservaId(), estado);
    }

    /**
     * El premio de un integrante (RF-TOR-007): primero la sancion (CA-02, se
     * decide una sola vez y queda anotada), luego los creditos y luego la
     * epica. Cada parte se anota en cuanto sale, y las dos son idempotentes en
     * su proveedor, asi que un reintento completa lo que falte sin repetir lo
     * hecho: nunca queda un premio a medias (CA-03).
     */
    private void premiar(Operacion operacion) {
        if (operacion.sancionVerificada() == null) {
            Operacion.Sancion sancion = sanciones.sancionado(operacion.jugadorUid())
                    ? Operacion.Sancion.SANCIONADO : Operacion.Sancion.SIN_SANCION;
            terminarParcial(operacion, op -> op.sancionVerificada(sancion, ahora()));
            operacion.sancionVerificada(sancion, ahora());
        }
        if (operacion.sancionVerificada() == Operacion.Sancion.SANCIONADO) {
            terminar(operacion, op -> op.excluida("sancion activa al premiar (CA-02 de HU-TOR-007)", ahora()));
            BITACORA.info("Premio excluido por sancion: clave={}", operacion.clave());
            return;
        }
        if (valor(operacion.monto()) > 0 && !operacion.creditosEntregados()) {
            libro.acreditar(operacion.jugadorUid(), operacion.monto(), operacion.clave(), CONCEPTO_PREMIO);
            terminarParcial(operacion, op -> op.creditosEntregados(ahora()));
            operacion.creditosEntregados(ahora());
        }
        if (operacion.productoId() != null && !operacion.epicaEntregada()) {
            inventario.entregarEpica(operacion.jugadorUid(), operacion.productoId(), "torneo-" + operacion.torneoId(),
                    operacion.claveDeEpica());
            terminarParcial(operacion, op -> op.epicaEntregada(ahora()));
            operacion.epicaEntregada(ahora());
        }
        Operacion entregado = operacion;
        transaccion.executeWithoutResult(estado -> {
            Operacion fresca = operaciones.findById(entregado.id()).orElseThrow();
            fresca.hecha("premio entregado", ahora());
            operaciones.save(fresca);
            avisarPremio(fresca);
        });
        BITACORA.info("Premio entregado: clave={} creditos={} epica={}", operacion.clave(), operacion.monto(),
                operacion.productoId());
    }

    private void avisarPremio(Operacion premio) {
        Torneo torneo = torneos.findById(premio.torneoId()).orElse(null);
        Equipo equipo = premio.equipoId() == null ? null : equipos.findById(premio.equipoId()).orElse(null);
        if (torneo == null || equipo == null) {
            return;
        }
        for (Operacion aviso : hitos.premioEntregado(torneo, equipo, premio, ahora())) {
            if (!operaciones.existsByClave(aviso.clave())) {
                operaciones.save(aviso);
            }
        }
    }

    private void notificar(Operacion operacion) {
        canales.notificar(operacion.jugadorUid(), operacion.clave(), operacion.titulo(), operacion.cuerpo(),
                operacion.creadaEn());
        terminar(operacion, op -> op.hecha("aviso entregado a la bandeja", ahora()));
    }

    private void enviarCorreo(Operacion operacion) {
        if (!canales.correoConfigurado()) {
            terminar(operacion, op -> op.omitida("correo sin configurar", ahora()));
            return;
        }
        AvisosAlJugador.Correo resultado = canales.enviarCorreo(operacion.jugadorUid(), operacion.torneoId(),
                operacion.titulo(), operacion.cuerpo(), operacion.clave());
        if (resultado == AvisosAlJugador.Correo.SIN_CONTACTO) {
            terminar(operacion, op -> op.omitida("ms-identidad no tiene contacto para el jugador", ahora()));
        } else {
            terminar(operacion, op -> op.hecha("correo encolado", ahora()));
        }
    }

    // ------------------------------------------------------------ apoyo

    private void fallar(Operacion operacion, String error, boolean reintentable) {
        OffsetDateTime ahora = ahora();
        Operacion fresca = operaciones.findById(operacion.id()).orElse(operacion);
        if (reintentable && !politica.agotada(fresca.intentos())) {
            OffsetDateTime siguiente = politica.siguiente(fresca.intentos(), ahora);
            terminar(operacion, op -> op.reintentable(error, siguiente, ahora));
            BITACORA.warn("Operacion {} ({}) fallo, intento {}: {}; siguiente {}", operacion.clave(), operacion.tipo(),
                    fresca.intentos(), error, siguiente);
        } else {
            String motivo = reintentable ? "agotados " + fresca.intentos() + " intentos: " + error : error;
            terminar(operacion, op -> op.fallida(motivo, ahora));
            BITACORA.error("Operacion {} ({}) requiere revision: {}", operacion.clave(), operacion.tipo(), motivo);
        }
    }

    /** Guarda el estado final de la operacion en su propia transaccion corta. */
    private void terminar(Operacion operacion, java.util.function.Consumer<Operacion> cambio) {
        transaccion.executeWithoutResult(estado -> {
            Operacion fresca = operaciones.findById(operacion.id()).orElseThrow();
            cambio.accept(fresca);
            operaciones.save(fresca);
        });
    }

    /** Anota un paso intermedio (sancion verificada, creditos o epica entregados) sin cerrar la operacion. */
    private void terminarParcial(Operacion operacion, java.util.function.Consumer<Operacion> cambio) {
        try {
            terminar(operacion, cambio);
        } catch (DataIntegrityViolationException imposible) {
            throw FalloDeIntegracion.pasajero("no se pudo anotar el paso intermedio", imposible);
        }
    }

    private static int valor(Integer monto) {
        return monto == null ? 0 : monto;
    }

    private OffsetDateTime ahora() {
        return OffsetDateTime.now(reloj);
    }
}
