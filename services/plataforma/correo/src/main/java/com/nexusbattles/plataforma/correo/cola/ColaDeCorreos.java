package com.nexusbattles.plataforma.correo.cola;

import com.nexusbattles.comun.observabilidad.FiltroDeTraza;
import com.nexusbattles.plataforma.correo.envio.Enmascarar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * La puerta de entrada de la cola: aceptar un correo es guardarlo.
 *
 * <p><b>Por que.</b> Hasta B1 el servicio respondia 202 y entregaba desde
 * memoria: un fallo del SMTP se descartaba y un reinicio perdia lo aceptado,
 * asi que un 202 no garantizaba nada. Desde el contrato 1.4.0 un 202
 * significa «guardado en la cola persistente»: la fila existe antes de
 * responder, y si la base no responde la excepcion sube hasta un 503
 * (ManejadorDeErroresDeCorreo). Nunca un 202 sin fila.
 *
 * <p>Es un solo {@code INSERT} en modo autocommit, que ya es su propia
 * transaccion: o queda la fila entera o no queda nada.
 *
 * <p><b>Idempotencia.</b> Con la cabecera {@code Idempotency-Key}, la misma
 * clave dos veces no encola dos correos (restriccion UNIQUE de la tabla). Es
 * lo que permite a un productor reintentar una llamada que agoto su tiempo
 * sin mandarle al jugador dos copias del mismo codigo.
 */
@Service
public class ColaDeCorreos {

    /** Largo maximo de Idempotency-Key (contrato 1.4.0). */
    public static final int LARGO_MAXIMO_CLAVE = 120;

    /**
     * Forma aceptable de un X-Trace-Id recibido. Lo que no la tenga se ignora
     * en vez de guardarse: un identificador de traza no puede ser una via
     * para meter texto arbitrario en la bitacora.
     */
    private static final Pattern TRAZA_VALIDA = Pattern.compile("[A-Za-z0-9._\\-]{1,64}");

    private static final Logger BITACORA = LoggerFactory.getLogger(ColaDeCorreos.class);

    private final RepositorioDeEnvios repositorio;
    private final MetricasDeCorreo metricas;
    private final Clock reloj;

    public ColaDeCorreos(RepositorioDeEnvios repositorio, MetricasDeCorreo metricas, Clock reloj) {
        this.repositorio = repositorio;
        this.metricas = metricas;
        this.reloj = reloj;
    }

    /**
     * Guarda el correo en la cola.
     *
     * @param pedido              el correo
     * @param claveDeIdempotencia cabecera Idempotency-Key (hasta 120
     *                            caracteres); nula o en blanco = sin clave
     * @param trazaRecibida       cabecera X-Trace-Id; nula = la traza de la
     *                            peticion (traceparent)
     * @return true si se guardo un envio nuevo; false si la clave ya existia
     */
    public boolean encolar(CorreoPedido pedido, String claveDeIdempotencia, String trazaRecibida) {
        String clave = claveDeIdempotencia == null || claveDeIdempotencia.isBlank()
                ? null
                : claveDeIdempotencia.trim();
        if (clave != null && clave.length() > LARGO_MAXIMO_CLAVE) {
            throw new IllegalArgumentException("Idempotency-Key admite hasta " + LARGO_MAXIMO_CLAVE + " caracteres");
        }
        EstadoDeEnvio estado = pedido.debeEnviarse() ? EstadoDeEnvio.PENDIENTE : EstadoDeEnvio.OMITIDO;
        NuevoEnvio envio = new NuevoEnvio(
                UUID.randomUUID(),
                pedido.plantilla().nombre(),
                pedido.destinatario(),
                pedido.asunto(),
                pedido.datos(),
                estado,
                pedido.motivoDeOmision(),
                clave,
                trazaDe(trazaRecibida),
                reloj.instant());

        if (!repositorio.insertar(envio)) {
            BITACORA.info(
                    "Correo {} para {} ya estaba en la cola con la misma Idempotency-Key: no se encola otro",
                    pedido.plantilla().nombre(),
                    Enmascarar.direccion(pedido.destinatario()));
            return false;
        }
        metricas.registrar(estado, pedido.plantilla().nombre());
        BITACORA.info(
                "Correo {} para {} guardado en la cola como {} (envio {})",
                pedido.plantilla().nombre(),
                Enmascarar.direccion(pedido.destinatario()),
                estado,
                envio.id());
        return true;
    }

    /**
     * La traza con la que se guarda el envio, para que la entrega -que ocurre
     * despues y en otro hilo- salga en la bitacora con la misma (regla 5).
     */
    static String trazaDe(String trazaRecibida) {
        if (trazaRecibida != null && TRAZA_VALIDA.matcher(trazaRecibida.trim()).matches()) {
            return trazaRecibida.trim();
        }
        String deLaPeticion = MDC.get(FiltroDeTraza.CLAVE_MDC);
        return deLaPeticion == null || deLaPeticion.isBlank() ? null : deLaPeticion;
    }
}
