package com.nexusbattles.plataforma.correo.cola;

import com.nexusbattles.comun.observabilidad.FiltroDeTraza;
import com.nexusbattles.plataforma.correo.envio.ClasificadorDeFallos;
import com.nexusbattles.plataforma.correo.envio.ComposicionDeCorreo;
import com.nexusbattles.plataforma.correo.envio.Enmascarar;
import com.nexusbattles.plataforma.correo.envio.EnviadorCorreoService;
import com.nexusbattles.plataforma.correo.envio.ResultadoDeEntrega;
import com.nexusbattles.plataforma.correo.template.Plantilla;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Vacia la cola: reclama envios vencidos, los entrega y anota el resultado.
 *
 * <p>Cada ronda ({@link #procesarRonda()}) hace tres cosas, en este orden:
 * <ol>
 *   <li><b>Recupera los atascados.</b> Un envio que lleva mas de
 *       {@code correo.entrega.atascado-tras} en ENVIANDO es uno que el servicio
 *       no llego a terminar (se cayo o se reinicio a mitad). Vuelve a la cola
 *       como un fallo transitorio mas: cuenta como intento y espera su turno.
 *       Puede que ese correo si hubiera salido -el SMTP lo acepto y la caida
 *       fue antes de anotarlo-: entonces el jugador lo recibe dos veces. Es el
 *       precio de no perder ninguno, y solo ocurre si el servicio muere en ese
 *       instante exacto.</li>
 *   <li><b>Reclama un lote</b> con {@code FOR UPDATE SKIP LOCKED}
 *       ({@link RepositorioDeEnvios#reclamar}).</li>
 *   <li><b>Entrega cada envio</b> fuera de toda transaccion: nunca se tiene
 *       una conexion a la base abierta mientras se espera al SMTP.</li>
 * </ol>
 *
 * <p>No esta programada aqui: la programa {@link ProgramacionDeEntrega}, de modo
 * que una prueba puede crear tantas instancias como quiera -dos a la vez, una
 * "despues de reiniciar"- y moverlas a mano sin que nada se ejecute solo.
 */
public class TrabajadorDeEntrega {

    static final String MOTIVO_INTERRUMPIDA =
            "entrega interrumpida a mitad de envio (el servicio se detuvo o se reinicio)";

    private static final Logger BITACORA = LoggerFactory.getLogger(TrabajadorDeEntrega.class);

    private final RepositorioDeEnvios repositorio;
    private final EnviadorCorreoService enviador;
    private final ComposicionDeCorreo composicion;
    private final MaquinaDeEstados maquina;
    private final ConfiguracionDeEntrega configuracion;
    private final MetricasDeCorreo metricas;
    private final TransactionOperations transacciones;
    private final Clock reloj;

    public TrabajadorDeEntrega(
            RepositorioDeEnvios repositorio,
            EnviadorCorreoService enviador,
            ComposicionDeCorreo composicion,
            MaquinaDeEstados maquina,
            ConfiguracionDeEntrega configuracion,
            MetricasDeCorreo metricas,
            TransactionOperations transacciones,
            Clock reloj) {
        this.repositorio = repositorio;
        this.enviador = enviador;
        this.composicion = composicion;
        this.maquina = maquina;
        this.configuracion = configuracion;
        this.metricas = metricas;
        this.transacciones = transacciones;
        this.reloj = reloj;
    }

    /**
     * Una ronda completa.
     *
     * @return cuantos envios se reclamaron (0 = la cola no tenia nada vencido)
     */
    public int procesarRonda() {
        recuperarAtascados();
        List<EnvioEnCola> lote = repositorio.reclamar(configuracion.lote(), reloj.instant());
        for (EnvioEnCola envio : lote) {
            procesar(envio);
        }
        return lote.size();
    }

    /**
     * Devuelve a la cola los envios interrumpidos, en una transaccion que los
     * mantiene bloqueados mientras se anotan (otra instancia que recupere a la
     * vez se los salta).
     *
     * @return cuantos se recuperaron
     */
    public int recuperarAtascados() {
        Instant ahora = reloj.instant();
        Instant limite = ahora.minus(configuracion.atascadoTras());
        Integer recuperados = transacciones.execute(estado -> {
            List<EnvioEnCola> atascados = repositorio.tomarAtascados(limite, configuracion.lote());
            for (EnvioEnCola envio : atascados) {
                CambioDeEstado cambio = maquina.tras(
                        Plantilla.deNombre(envio.plantilla()),
                        envio.datos(),
                        envio.intentos(),
                        ResultadoDeEntrega.fallido(false, MOTIVO_INTERRUMPIDA),
                        ahora);
                if (repositorio.registrarResultado(envio.id(), envio.intentos(), cambio, ahora)) {
                    metricas.registrar(cambio.estado(), envio.plantilla());
                    BITACORA.warn(
                            "Correo {} para {} (envio {}) interrumpido a mitad de envio; queda {}",
                            envio.plantilla(),
                            Enmascarar.direccion(envio.destinatario()),
                            envio.id(),
                            cambio.estado());
                }
            }
            return atascados.size();
        });
        return recuperados == null ? 0 : recuperados;
    }

    /**
     * Borra los envios terminados que ya pasaron el plazo de retencion.
     *
     * @return cuantos se borraron
     */
    public int purgar() {
        Instant limite = reloj.instant().minus(Duration.ofDays(configuracion.retencionDias()));
        int borrados = repositorio.purgarTerminadosAntesDe(limite);
        if (borrados > 0) {
            BITACORA.info(
                    "Purga de retencion: {} envios terminados hace mas de {} dias", borrados, configuracion.retencionDias());
        }
        return borrados;
    }

    /**
     * Entrega un envio ya reclamado.
     *
     * @return false si al ir a enviarlo ya no era de este trabajador
     */
    boolean procesar(EnvioEnCola envio) {
        if (!repositorio.renovarReclamo(envio.id(), envio.intentos(), reloj.instant())) {
            BITACORA.info("Envio {} ya no es de este trabajador (otro lo recupero o lo reclamo): no se envia",
                    envio.id());
            return false;
        }
        String trazaPrevia = MDC.get(FiltroDeTraza.CLAVE_MDC);
        if (envio.trazaId() != null) {
            // La entrega sale en la bitacora con la traza de la peticion que
            // la pidio (regla 5): la cola es el "mensaje de cola" de la regla.
            MDC.put(FiltroDeTraza.CLAVE_MDC, envio.trazaId());
        }
        try {
            Optional<Plantilla> plantilla = Plantilla.deNombre(envio.plantilla());
            ResultadoDeEntrega resultado = plantilla
                    .map(p -> entregar(p, envio))
                    .orElseGet(() -> ResultadoDeEntrega.fallido(
                            true, "plantilla desconocida para esta version del servicio: " + envio.plantilla()));
            Instant ahora = reloj.instant();
            CambioDeEstado cambio = maquina.tras(plantilla, envio.datos(), envio.intentos(), resultado, ahora);
            if (!repositorio.registrarResultado(envio.id(), envio.intentos(), cambio, ahora)) {
                BITACORA.warn("El resultado del envio {} ({}) no se anoto: otro trabajador ya lo habia reclamado",
                        envio.id(), cambio.estado());
                return true;
            }
            metricas.registrar(cambio.estado(), envio.plantilla());
            anotarEnBitacora(envio, cambio);
            return true;
        } finally {
            if (trazaPrevia == null) {
                MDC.remove(FiltroDeTraza.CLAVE_MDC);
            } else {
                MDC.put(FiltroDeTraza.CLAVE_MDC, trazaPrevia);
            }
        }
    }

    private ResultadoDeEntrega entregar(Plantilla plantilla, EnvioEnCola envio) {
        try {
            return enviador.enviar(
                    envio.destinatario(),
                    envio.asunto(),
                    plantilla.ruta(),
                    composicion.variables(plantilla, envio.destinatario(), envio.datos()));
        } catch (RuntimeException e) {
            // El enviador no lanza. Si algo llega aqui es un fallo del propio
            // servicio, no del correo: se reintenta, por si lo arregla un
            // despliegue antes de agotar los intentos.
            return ResultadoDeEntrega.fallido(false, ClasificadorDeFallos.resumen(e));
        }
    }

    /**
     * Una linea por intento. Nivel INFO para lo que salio y WARN para lo que
     * no; siempre con el destinatario enmascarado y nunca con los datos del
     * correo -en dos de ellos van codigos de un solo uso-.
     */
    private void anotarEnBitacora(EnvioEnCola envio, CambioDeEstado cambio) {
        String destinatario = Enmascarar.direccion(envio.destinatario());
        switch (cambio.estado()) {
            case ENVIADO, DESVIADO -> BITACORA.info(
                    "Correo {} {} por {} para {} (envio {}, intento {}, id {})",
                    envio.plantilla(), cambio.estado(), cambio.destino(), destinatario, envio.id(),
                    envio.intentos(), cambio.identificador());
            case OMITIDO -> BITACORA.info(
                    "Correo {} para {} OMITIDO (envio {}): {}",
                    envio.plantilla(), destinatario, envio.id(), cambio.error());
            case ERROR_REINTENTABLE -> BITACORA.warn(
                    "Correo {} para {} no salio (envio {}, intento {}); se reintenta a las {}: {}",
                    envio.plantilla(), destinatario, envio.id(), envio.intentos(), cambio.proximoIntento(),
                    cambio.error());
            default -> BITACORA.warn(
                    "Correo {} para {} {} (envio {}, intento {}): {}",
                    envio.plantilla(), destinatario, cambio.estado(), envio.id(), envio.intentos(),
                    cambio.error());
        }
    }
}
