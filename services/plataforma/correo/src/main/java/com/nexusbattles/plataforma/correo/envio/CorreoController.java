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

    private final EnviadorCorreoService enviador;

    public CorreoController(EnviadorCorreoService enviador) {
        this.enviador = enviador;
    }

    @PostMapping("/bienvenida")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void enviarBienvenida(@Valid @RequestBody CorreoBienvenidaRequest solicitud) {
        enviador.enviar(
                solicitud.email(),
                "Bienvenido a The Nexus Battles VI",
                "email/bienvenida",
                Map.of("saludo", solicitud.saludo()));
    }

    @PostMapping("/aviso-acceso")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void enviarAvisoAcceso(@Valid @RequestBody CorreoAvisoAccesoRequest solicitud) {
        enviador.enviar(
                solicitud.email(),
                "Acceso desde un dispositivo no reconocido",
                "email/aviso-acceso",
                Map.of(
                        "apodo", solicitud.apodo(),
                        "ip", solicitud.ip(),
                        "fechaHora", solicitud.fechaHoraLegible()));
    }

    @PostMapping("/recuperacion-clave")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void enviarRecuperacionClave(@Valid @RequestBody CorreoRecuperacionClaveRequest solicitud) {
        // El codigo no se registra en bitacora en ningun punto: un OTP en los
        // logs es un OTP filtrado.
        enviador.enviar(
                solicitud.email(),
                "Recupera tu contraseña de The Nexus Battles VI",
                "email/recuperacion-clave",
                Map.of(
                        "apodo", solicitud.apodo(),
                        "codigo", solicitud.codigo(),
                        "minutosVigencia", solicitud.minutosVigencia()));
    }
}
