package com.nexusbattles.ms_finanzas.pagos.service;

import com.nexusbattles.ms_finanzas.pagos.correo.ConfirmacionCompraClient;
import com.nexusbattles.ms_finanzas.pagos.correo.ConfirmacionCompraRequest;
import com.nexusbattles.ms_finanzas.pagos.dto.PagoDTOs.ProcesarPagoRequest;
import com.nexusbattles.ms_finanzas.pagos.dto.PagoDTOs.ProcesarPagoResponse;
import com.nexusbattles.ms_finanzas.pagos.pasarela.PasarelaSimuladaClient;
import com.nexusbattles.ms_finanzas.pagos.pasarela.PasarelaSimuladaClient.PasarelaNoDisponibleException;
import com.nexusbattles.ms_finanzas.pagos.pasarela.PasarelaSimuladaClient.RespuestaPasarela;
import com.nexusbattles.ms_finanzas.pagos.pasarela.PasarelaSimuladaClient.ResultadoPago;
import com.nexusbattles.ms_finanzas.pagos.pasarela.PasarelaSimuladaClient.SolicitudPago;
import com.nexusbattles.ms_finanzas.transacciones.RegistrarTransaccionRequest;
import com.nexusbattles.ms_finanzas.transacciones.ResultadoTransaccion;
import com.nexusbattles.ms_finanzas.transacciones.Transaccion;
import com.nexusbattles.ms_finanzas.transacciones.TransaccionRegistroService;
import com.nexusbattles.ms_finanzas.transacciones.TransaccionYaRegistradaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Orquesta el flujo completo de un pago en dinero real (HU-PAG-001):
 * 1. Si el refId ya fue procesado antes, devuelve la misma respuesta sin
 *    repetir nada — ni la llamada a la pasarela ni el correo (ver nota de
 *    idempotencia más abajo).
 * 2. Si no, llama a la pasarela simulada, con reintento controlado ante
 *    fallo transitorio.
 * 3. Registra el resultado en el historial permanente (Sanabria, HU-PAG-002).
 * 4. Marca para revisión manual si el monto supera el umbral de alto valor.
 * 5. Si quedó APROBADO, dispara el correo de confirmación de compra
 *    (Anaya, HU-PAG-003/#537) — no bloqueante: si el correo falla, el pago
 *    ya aprobado no se revierte.
 *
 * NOTA DE IDEMPOTENCIA (hallazgo del 23-sep, reportado por Julián al
 * integrar HU-CAR-010): el registro en el historial ya estaba protegido
 * contra reintentos (TransaccionYaRegistradaException), pero el correo NO
 * — un reintento del llamador con el mismo refId disparaba un segundo
 * correo de confirmación al jugador, aunque el pago ya estuviera
 * registrado desde el primer intento. Se corrige comprobando primero si
 * el refId ya existe, antes de tocar la pasarela o el correo.
 *
 * NOTA: el umbral de "alto valor" (RF-PAG-006) todavía no lo define el
 * Product Owner. Mientras tanto se usa un valor configurable de ejemplo.
 */
@Service
public class PagoService {

    private static final Logger log = LoggerFactory.getLogger(PagoService.class);

    private static final int INTENTOS_MAXIMOS = 3;

    private final PasarelaSimuladaClient pasarela;
    private final TransaccionRegistroService transacciones;
    private final ConfirmacionCompraClient correo;
    private final BigDecimal umbralAltoValor;

    public PagoService(PasarelaSimuladaClient pasarela,
                       TransaccionRegistroService transacciones,
                       ConfirmacionCompraClient correo) {
        this.pasarela = pasarela;
        this.transacciones = transacciones;
        this.correo = correo;
        // Confirmado por el profesor (PO): USD 3000. El sistema hoy no
        // distingue moneda al comparar (compara el numero tal cual venga en
        // `monto`, sin convertir); si el monto llega en una moneda distinta
        // a USD, esta comparacion no es exacta. Pendiente aclarar con el
        // profesor si hace falta conversion, o si el proyecto asume que todo
        // pago relevante para este umbral ya viene en USD/COP equivalente.
        this.umbralAltoValor = new BigDecimal("3000.00");
    }

    public ProcesarPagoResponse procesar(ProcesarPagoRequest solicitud) {
        // Idempotencia: si este refId ya se proceso antes, no se repite
        // nada — ni la pasarela, ni el registro, ni el correo. Se devuelve
        // la misma respuesta que se le dio la primera vez.
        Optional<Transaccion> yaRegistrada = transacciones.buscarPorRefId(solicitud.refId());
        if (yaRegistrada.isPresent()) {
            return respuestaDesdeTransaccionExistente(yaRegistrada.get(), solicitud);
        }

        RespuestaPasarela respuesta = procesarConReintento(solicitud);

        boolean esAltoValor = solicitud.monto() != null
            && solicitud.monto().compareTo(umbralAltoValor) > 0;

        ResultadoTransaccion resultadoParaHistorial = switch (respuesta.resultado()) {
            case APROBADA -> ResultadoTransaccion.APROBADO;
            case RECHAZADA -> ResultadoTransaccion.RECHAZADO;
            case INDETERMINADA -> ResultadoTransaccion.INDETERMINADO;
        };

        try {
            transacciones.registrar(new RegistrarTransaccionRequest(
                solicitud.refId(),
                solicitud.uidUsuario(),
                solicitud.monto(),
                solicitud.moneda(),
                solicitud.concepto(),
                resultadoParaHistorial,
                null,
                respuesta.referenciaExterna()
            ));
        } catch (TransaccionYaRegistradaException yaRegistradaCarrera) {
            // Dos requests concurrentes con el mismo refId llegaron a la vez
            // y ambas pasaron el chequeo de arriba antes de que la primera
            // terminara de registrar. Se recupera la ya registrada y se
            // responde con ella, igual que si se hubiera detectado desde
            // el principio.
            Optional<Transaccion> registradaPorLaOtra = transacciones.buscarPorRefId(solicitud.refId());
            if (registradaPorLaOtra.isPresent()) {
                return respuestaDesdeTransaccionExistente(registradaPorLaOtra.get(), solicitud);
            }
        }

        // Correo de confirmación: solo si quedó aprobado, y solo llegamos
        // aquí si es la primera vez que se procesa este refId.
        if (respuesta.resultado() == ResultadoPago.APROBADA) {
            correo.enviarConfirmacionCompra(new ConfirmacionCompraRequest(
                solicitud.uidUsuario(), // TODO: reemplazar por el email real
                solicitud.uidUsuario(), // TODO: reemplazar por el apodo real
                solicitud.monto(),
                solicitud.moneda(),
                solicitud.concepto(),
                OffsetDateTime.now()
            ));
        }

        String mensaje = switch (respuesta.resultado()) {
            case RECHAZADA -> respuesta.motivoRechazo();
            case INDETERMINADA -> "No se pudo confirmar el pago con la pasarela. Queda marcado para conciliación manual.";
            case APROBADA -> "Pago procesado";
        };

        return new ProcesarPagoResponse(
            solicitud.refId(),
            resultadoParaHistorial.name(),
            mensaje,
            esAltoValor
        );
    }

    private ProcesarPagoResponse respuestaDesdeTransaccionExistente(Transaccion transaccion, ProcesarPagoRequest solicitud) {
        boolean esAltoValor = solicitud.monto() != null
            && solicitud.monto().compareTo(umbralAltoValor) > 0;
        String mensaje = switch (transaccion.getResultado()) {
            case RECHAZADO -> "Pago rechazado";
            case INDETERMINADO -> "No se pudo confirmar el pago con la pasarela. Queda marcado para conciliación manual.";
            case APROBADO -> "Pago procesado";
        };
        return new ProcesarPagoResponse(
            solicitud.refId(),
            transaccion.getResultado().name(),
            mensaje,
            esAltoValor
        );
    }

    private RespuestaPasarela procesarConReintento(ProcesarPagoRequest solicitud) {
        SolicitudPago solicitudPasarela = new SolicitudPago(
            solicitud.uidUsuario(),
            solicitud.monto(),
            solicitud.moneda(),
            solicitud.concepto(),
            solicitud.refId()
        );

        for (int intento = 1; intento <= INTENTOS_MAXIMOS; intento++) {
            try {
                return pasarela.procesar(solicitudPasarela);
            } catch (PasarelaNoDisponibleException fallo) {
                log.warn("Intento {}/{} fallido contra la pasarela simulada para refId={}: {}",
                    intento, INTENTOS_MAXIMOS, solicitud.refId(), fallo.getMessage());
                if (intento == INTENTOS_MAXIMOS) {
                    log.error("Se agotaron los reintentos contra la pasarela para refId={}. " +
                        "Se marca como INDETERMINADA para conciliación manual.", solicitud.refId());
                    return new RespuestaPasarela(
                        ResultadoPago.INDETERMINADA,
                        null,
                        "La pasarela no respondió tras " + INTENTOS_MAXIMOS + " intentos."
                    );
                }
            }
        }

        throw new IllegalStateException("No debería llegar aquí");
    }
}
