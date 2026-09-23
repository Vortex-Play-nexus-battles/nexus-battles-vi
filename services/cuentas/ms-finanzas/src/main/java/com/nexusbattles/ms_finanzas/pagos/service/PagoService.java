package com.nexusbattles.ms_finanzas.pagos.service;

import com.nexusbattles.ms_finanzas.pagos.dto.PagoDTOs.ProcesarPagoRequest;
import com.nexusbattles.ms_finanzas.pagos.dto.PagoDTOs.ProcesarPagoResponse;
import com.nexusbattles.ms_finanzas.pagos.pasarela.PasarelaSimuladaClient;
import com.nexusbattles.ms_finanzas.pagos.pasarela.PasarelaSimuladaClient.RespuestaPasarela;
import com.nexusbattles.ms_finanzas.pagos.pasarela.PasarelaSimuladaClient.ResultadoPago;
import com.nexusbattles.ms_finanzas.pagos.pasarela.PasarelaSimuladaClient.SolicitudPago;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Orquesta el flujo completo de un pago en dinero real (HU-PAG-001):
 * 1. Llama a la pasarela simulada.
 * 2. Registra el resultado en el historial (HU-PAG-002, servicio de Sanabria).
 * 3. Marca para revisión manual si el monto supera el umbral de alto valor.
 *
 * NOTA: el umbral de "alto valor" (RF-PAG-006) todavía no lo define el
 * Product Owner — ver "Preguntas para el cliente" en el backlog. Mientras
 * tanto se usa un valor configurable, con un default de ejemplo.
 */
@Service
public class PagoService {

    private final PasarelaSimuladaClient pasarela;
    private final BigDecimal umbralAltoValor;

    public PagoService(PasarelaSimuladaClient pasarela) {
        this.pasarela = pasarela;
        // TODO: mover a application.properties cuando el PO confirme el umbral real.
        this.umbralAltoValor = new BigDecimal("1000.00");
    }

    public ProcesarPagoResponse procesar(ProcesarPagoRequest solicitud) {
        RespuestaPasarela respuesta = pasarela.procesar(new SolicitudPago(
            solicitud.uidUsuario(),
            solicitud.monto(),
            solicitud.moneda(),
            solicitud.concepto(),
            solicitud.refId()
        ));

        boolean esAltoValor = solicitud.monto() != null
            && solicitud.monto().compareTo(umbralAltoValor) > 0;

        // TODO (siguiente paso): aquí se llamará a POST /transacciones
        // (TransaccionRegistroService de Sanabria) para dejar el registro
        // permanente, y si queda APROBADA, se disparará el correo de
        // confirmación (ConfirmacionCompraService, archivo pendiente).

        String estado = switch (respuesta.resultado()) {
            case APROBADA -> "APROBADA";
            case RECHAZADA -> "RECHAZADA";
            case INDETERMINADA -> "INDETERMINADA";
        };

        String mensaje = respuesta.resultado() == ResultadoPago.RECHAZADA
            ? respuesta.motivoRechazo()
            : "Pago procesado";

        return new ProcesarPagoResponse(
            solicitud.refId(),
            estado,
            mensaje,
            esAltoValor
        );
    }
}
