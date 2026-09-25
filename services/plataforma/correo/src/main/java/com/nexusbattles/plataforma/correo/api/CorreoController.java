package com.nexusbattles.plataforma.correo.api;

import com.nexusbattles.plataforma.correo.cola.ColaDeCorreos;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Punto de entrada para que cualquier microservicio dispare un correo.
 * Contrato: contracts/openapi/correo.yaml
 *
 * <p>Hay una ruta por tipo de correo en vez de una genérica con un campo
 * "tipo": así la validación comprueba que lleguen los datos que esa plantilla
 * necesita. Añadir tipos nuevos es añadir rutas, que no rompe a quien ya consume.
 *
 * <p><b>202 = guardado en la cola persistente</b> (contrato 1.4.0), no
 * «entregado»: la entrega la hace después el trabajador de la cola, con
 * reintentos. Si la cola no está disponible, la respuesta es 503 y no se
 * guarda nada (ManejadorDeErroresDeCorreo): nunca un 202 sin fila.
 *
 * <p>Cada ruta acepta dos cabeceras opcionales del contrato:
 * {@code Idempotency-Key} (hasta 120 caracteres: la misma clave no encola dos
 * correos) y {@code X-Trace-Id} (la traza con la que se anotará la entrega).
 * Cada cuerpo sabe qué correo pide ({@code aCorreo()}); aquí solo se encola.
 */
@RestController
@RequestMapping("/api/v1/correos")
public class CorreoController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String TRACE_ID = "X-Trace-Id";

    private final ColaDeCorreos cola;

    public CorreoController(ColaDeCorreos cola) {
        this.cola = cola;
    }

    @PostMapping("/bienvenida")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void enviarBienvenida(
            @Valid @RequestBody CorreoBienvenidaRequest solicitud,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) @Size(max = ColaDeCorreos.LARGO_MAXIMO_CLAVE) String clave,
            @RequestHeader(name = TRACE_ID, required = false) String traza) {
        cola.encolar(solicitud.aCorreo(), clave, traza);
    }

    @PostMapping("/aviso-acceso")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void enviarAvisoAcceso(
            @Valid @RequestBody CorreoAvisoAccesoRequest solicitud,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) @Size(max = ColaDeCorreos.LARGO_MAXIMO_CLAVE) String clave,
            @RequestHeader(name = TRACE_ID, required = false) String traza) {
        cola.encolar(solicitud.aCorreo(), clave, traza);
    }

    @PostMapping("/cambio-clave")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void enviarCambioClave(
            @Valid @RequestBody CorreoCambioClaveRequest solicitud,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) @Size(max = ColaDeCorreos.LARGO_MAXIMO_CLAVE) String clave,
            @RequestHeader(name = TRACE_ID, required = false) String traza) {
        cola.encolar(solicitud.aCorreo(), clave, traza);
    }

    @PostMapping("/confirmacion-cuenta")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void enviarConfirmacionCuenta(
            @Valid @RequestBody CorreoConfirmacionCuentaRequest solicitud,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) @Size(max = ColaDeCorreos.LARGO_MAXIMO_CLAVE) String clave,
            @RequestHeader(name = TRACE_ID, required = false) String traza) {
        cola.encolar(solicitud.aCorreo(), clave, traza);
    }

    @PostMapping("/recuperacion-clave")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void enviarRecuperacionClave(
            @Valid @RequestBody CorreoRecuperacionClaveRequest solicitud,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) @Size(max = ColaDeCorreos.LARGO_MAXIMO_CLAVE) String clave,
            @RequestHeader(name = TRACE_ID, required = false) String traza) {
        cola.encolar(solicitud.aCorreo(), clave, traza);
    }

    @PostMapping("/mision")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void enviarCorreoMision(
            @Valid @RequestBody CorreoMisionRequest solicitud,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) @Size(max = ColaDeCorreos.LARGO_MAXIMO_CLAVE) String clave,
            @RequestHeader(name = TRACE_ID, required = false) String traza) {
        // HU-COR-005: con debeEnviarCorreo=false se responde 202 igual; el
        // correo queda en la cola como OMITIDO y no sale.
        cola.encolar(solicitud.aCorreo(), clave, traza);
    }

    @PostMapping("/subasta")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void enviarCorreoSubasta(
            @Valid @RequestBody CorreoSubastaRequest solicitud,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) @Size(max = ColaDeCorreos.LARGO_MAXIMO_CLAVE) String clave,
            @RequestHeader(name = TRACE_ID, required = false) String traza) {
        cola.encolar(solicitud.aCorreo(), clave, traza);
    }

    @PostMapping("/confirmacion-compra")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void enviarConfirmacionCompra(
            @Valid @RequestBody CorreoConfirmacionCompraRequest solicitud,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) @Size(max = ColaDeCorreos.LARGO_MAXIMO_CLAVE) String clave,
            @RequestHeader(name = TRACE_ID, required = false) String traza) {
        // HU-PAG-003 (issue #537). ms-finanzas ya aprobo el pago antes de
        // llamar aqui: este servicio no valida el monto ni la transaccion,
        // solo la transcribe al correo.
        cola.encolar(solicitud.aCorreo(), clave, traza);
    }

    @PostMapping("/sancion")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void enviarCorreoSancion(
            @Valid @RequestBody CorreoSancionRequest solicitud,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) @Size(max = ColaDeCorreos.LARGO_MAXIMO_CLAVE) String clave,
            @RequestHeader(name = TRACE_ID, required = false) String traza) {
        // 7.3.2: la suspension y el baneo se notifican al correo. Lo decide
        // moderacion-sanciones; aqui solo se transcribe.
        cola.encolar(solicitud.aCorreo(), clave, traza);
    }
}
