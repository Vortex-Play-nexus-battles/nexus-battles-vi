package com.nexusbattles.ms_finanzas.transacciones;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registro de transacciones en moneda real (HU-PAG-002, RF-PAG-002). Lo llama
 * el servicio que integra la pasarela de pagos simulada (HU-PAG-001) después
 * de cada intento de cobro —aprobado, rechazado o indeterminado— para dejarlo
 * trazado de forma permanente y consultable en {@code GET /transacciones/mi-historial}.
 *
 * <p><b>Seguridad:</b> {@code SecurityConfig} exige {@code ROLE_SERVICIO} para
 * este endpoint (ADR-005) — un jugador no registra sus propias transacciones;
 * solo el servicio que habló con la pasarela sabe si el cobro fue aprobado.
 *
 * <p>Idempotente por {@code refId} (regla del propio {@link TransaccionRegistroService}):
 * un reintento del llamador por timeout responde 409 {@code transaccion-ya-registrada}
 * en vez de duplicar el asiento.
 */
@RestController
@RequestMapping("/transacciones")
public class TransaccionRegistroController {

    private final TransaccionRegistroService servicio;

    public TransaccionRegistroController(TransaccionRegistroService servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    public ResponseEntity<ResumenTransaccion> registrar(@RequestBody RegistrarTransaccionApiRequest peticion) {
        Transaccion transaccion = servicio.registrar(new RegistrarTransaccionRequest(
                peticion.refId(),
                peticion.uidUsuario(),
                peticion.monto(),
                peticion.moneda(),
                peticion.concepto(),
                peticion.resultado(),
                peticion.comprobanteUrl(),
                peticion.pasarelaRefExterna()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ResumenTransaccion.desde(transaccion));
    }
}
