package com.nexusbattles.plataforma.correo.envio;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Punto de entrada para que cualquier microservicio dispare un correo.
 * Contrato: contracts/openapi/correo.yaml
 *
 * <p>Hay una ruta por tipo de correo en vez de una genérica con un campo
 * "tipo": así la validación comprueba que lleguen los datos que esa plantilla
 * necesita. Añadir tipos nuevos es añadir rutas, que no rompe a quien ya consume.
 *
 * <p>Responde 202 y no 200 a propósito: el correo quedó entregado al servidor
 * SMTP, que no es lo mismo que haber llegado a la bandeja del destinatario.
 */
@RestController
@RequestMapping("/api/v1/correos")
public class CorreoController {

    private final EntregaEnSegundoPlano enviador;

    public CorreoController(EntregaEnSegundoPlano enviador) {
        this.enviador = enviador;
    }

    @PostMapping("/bienvenida")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void enviarBienvenida(@Valid @RequestBody CorreoBienvenidaRequest solicitud) {
        enviador.entregar(
                solicitud.email(),
                "Bienvenido a The Nexus Battles VI",
                "email/bienvenida",
                Map.of("saludo", solicitud.saludo()));
    }

    @PostMapping("/aviso-acceso")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void enviarAvisoAcceso(@Valid @RequestBody CorreoAvisoAccesoRequest solicitud) {
        enviador.entregar(
                solicitud.email(),
                "Acceso desde un dispositivo no reconocido",
                "email/aviso-acceso",
                Map.of(
                        "apodo", solicitud.apodo(),
                        "ip", solicitud.ip(),
                        "fechaHora", solicitud.fechaHoraLegible()));
    }

    @PostMapping("/cambio-clave")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void enviarCambioClave(@Valid @RequestBody CorreoCambioClaveRequest solicitud) {
        // HU-AUT-006 CA-01. Mismo caracter que el aviso de acceso: informa
        // de algo que YA paso para que quien no lo hizo reaccione. Nunca
        // lleva la contraseña, ni entra en bitacora nada mas que el destino.
        enviador.entregar(
                solicitud.email(),
                "Tu contraseña de The Nexus Battles VI cambió",
                "email/cambio-clave",
                Map.of(
                        "apodo", solicitud.apodo(),
                        "ip", solicitud.ip(),
                        "fechaHora", solicitud.fechaHoraLegible()));
    }

    @PostMapping("/confirmacion-cuenta")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void enviarConfirmacionCuenta(@Valid @RequestBody CorreoConfirmacionCuentaRequest solicitud) {
        // HU-COR-002. Igual que en recuperacion-clave: el codigo no se registra
        // en bitacora en ningun punto. Un codigo de activacion en los logs es
        // una cuenta activable por quien lea los logs.
        enviador.entregar(
                solicitud.email(),
                "Confirma tu cuenta de The Nexus Battles VI",
                "email/confirmacion-cuenta",
                Map.of(
                        "apodo", solicitud.apodo(),
                        "codigo", solicitud.codigo(),
                        "minutosVigencia", solicitud.minutosVigencia()));
    }

    @PostMapping("/recuperacion-clave")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void enviarRecuperacionClave(@Valid @RequestBody CorreoRecuperacionClaveRequest solicitud) {
        // El codigo no se registra en bitacora en ningun punto: un OTP en los
        // logs es un OTP filtrado.
        enviador.entregar(
                solicitud.email(),
                "Recupera tu contraseña de The Nexus Battles VI",
                "email/recuperacion-clave",
                Map.of(
                        "apodo", solicitud.apodo(),
                        "codigo", solicitud.codigo(),
                        "minutosVigencia", solicitud.minutosVigencia()));
    }

    @PostMapping("/mision")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void enviarCorreoMision(@Valid @RequestBody CorreoMisionRequest solicitud) {
        // HU-COR-005: este servicio no decide si corresponde enviar. Si viene
        // debeEnviarCorreo=false (avisos solo dentro de la app, o categoria
        // apagada -- CA-02/CA-03), se responde 202 igual pero no se envia
        // nada. El registro de esa supresion es de quien llama.
        if (!solicitud.debeEnviarCorreo()) {
            return;
        }
        enviador.entregar(
                solicitud.email(),
                solicitud.asunto(),
                "email/mision",
                Map.of(
                        "apodo", solicitud.apodo(),
                        "asunto", solicitud.asunto(),
                        "mensaje", solicitud.mensaje()));
    }

    @PostMapping("/subasta")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void enviarCorreoSubasta(@Valid @RequestBody CorreoSubastaRequest solicitud) {
        // Mismo criterio que enviarCorreoMision: la preferencia ya viene
        // resuelta por quien llama.
        if (!solicitud.debeEnviarCorreo()) {
            return;
        }
        enviador.entregar(
                solicitud.email(),
                solicitud.asunto(),
                "email/subasta",
                Map.of(
                        "apodo", solicitud.apodo(),
                        "asunto", solicitud.asunto(),
                        "mensaje", solicitud.mensaje()));
    }

    @PostMapping("/confirmacion-compra")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void enviarConfirmacionCompra(@Valid @RequestBody CorreoConfirmacionCompraRequest solicitud) {
        // HU-PAG-003 (issue #537). ms-finanzas ya aprobo el pago antes de
        // llamar aqui: este servicio no valida el monto ni la transaccion,
        // solo la transcribe al correo.
        enviador.entregar(
                solicitud.email(),
                "Confirmación de tu compra en The Nexus Battles VI",
                "email/confirmacion-compra",
                Map.of(
                        "apodo", solicitud.apodo(),
                        "monto", solicitud.montoFormateado(),
                        "concepto", solicitud.concepto(),
                        "fechaHora", solicitud.fechaHoraLegible()));
    }
}
