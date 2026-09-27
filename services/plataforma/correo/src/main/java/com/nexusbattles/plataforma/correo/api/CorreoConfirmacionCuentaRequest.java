package com.nexusbattles.plataforma.correo.api;

import com.nexusbattles.plataforma.correo.cola.CorreoPedido;
import com.nexusbattles.plataforma.correo.envio.PropositoDeConfirmacion;
import com.nexusbattles.plataforma.correo.template.Plantilla;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Datos del correo de confirmación de cuenta (HU-COR-002, RNF-COR-002).
 *
 * <p>Mismo reparto de responsabilidades que en HU-COR-003: este servicio solo
 * renderiza y envía el correo. Generar el código de forma segura, guardarlo
 * con su marca de tiempo, validarlo, invalidar el anterior al reenviar y
 * bloquear tras varios intentos fallidos corresponde a ms-identidad, que es
 * quien registra la cuenta y la deja «pendiente de verificación». Identidad
 * y control de acceso están fuera del alcance del Equipo 6 (project-charter.md).
 *
 * <p>Desde la 1.4.0 el mismo correo sirve a dos situaciones ({@code proposito}):
 * el jugador que se acaba de registrar y tiene que probar que el buzón es suyo
 * ({@code VERIFICACION}), y la cuenta administrativa creada por un Super
 * Administrador, cuya dueña tiene que elegir su contraseña ({@code ACTIVACION},
 * por omisión porque es lo que ms-identidad ya enviaba).
 */
public record CorreoConfirmacionCuentaRequest(
        @NotBlank @Email String email,
        @NotBlank String apodo,

        /*
         * No se valida que sean seis dígitos: eso acoplaría este servicio al
         * formato que hoy usa ms-identidad. Si mañana lo cambian, el correo
         * seguiría funcionando.
         */
        @NotBlank @Size(max = 12) String codigo,

        /*
         * La vigencia la manda quien generó el código: el issue pide que sea
         * configurable por variable de entorno del lado de quien lo emite, y
         * así el correo nunca promete un plazo distinto del real.
         */
        @NotNull @Min(1) Integer minutosVigencia,

        PropositoDeConfirmacion proposito) {

    public CorreoConfirmacionCuentaRequest {
        proposito = proposito == null ? PropositoDeConfirmacion.ACTIVACION : proposito;
    }

    public CorreoPedido aCorreo() {
        // El codigo NO se registra en bitacora en ningun punto: un codigo de
        // activacion en los logs es una cuenta activable por quien los lea.
        // En la cola vive solo hasta que el envio termina.
        String asunto = proposito == PropositoDeConfirmacion.VERIFICACION
                ? "Confirma tu cuenta de The Nexus Battles VI"
                : "Activa tu cuenta de The Nexus Battles VI";
        return CorreoPedido.paraEnviar(
                Plantilla.CONFIRMACION_CUENTA,
                email,
                asunto,
                Map.of(
                        "apodo", apodo,
                        "codigo", codigo,
                        "minutosVigencia", minutosVigencia,
                        "proposito", proposito.name()));
    }
}
