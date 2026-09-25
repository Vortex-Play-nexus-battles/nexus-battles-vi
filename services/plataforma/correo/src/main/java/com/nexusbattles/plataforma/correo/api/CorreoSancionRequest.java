package com.nexusbattles.plataforma.correo.api;

import com.nexusbattles.plataforma.correo.cola.CorreoPedido;
import com.nexusbattles.plataforma.correo.template.Plantilla;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Notificación de una sanción al correo del jugador (contrato 1.4.0,
 * {@code CorreoSancionRequest}).
 *
 * <p>El 7.3.2 del documento del curso exige «notificación automática al correo
 * electrónico del usuario» de la suspensión y «notificación formal al correo
 * electrónico registrado» del baneo, y el 7.3.7 lista lo que cada aviso debe
 * llevar: la acción tomada, el motivo, el proceso de apelación si aplica y la
 * fecha de vigencia si aplica. Lo dispara moderacion-sanciones, que es quien
 * decide la sanción: este servicio solo la transcribe.
 *
 * <p>{@code apodo} y {@code motivo} se exigen no vacíos aunque el contrato solo
 * los marque obligatorios: un aviso de sanción sin motivo incumple el 7.3.7, y
 * sin apodo el saludo queda cojo.
 *
 * @param hasta              fin de la suspensión (solo SUSPENSION)
 * @param apelableHasta      último día para apelar (30 días, 7.3.2)
 * @param resultadoApelacion solo APELACION_RESUELTA
 */
public record CorreoSancionRequest(
        @NotBlank @Email String email,
        @NotBlank String apodo,
        @NotNull TipoDeSancion tipo,
        @NotBlank String motivo,
        OffsetDateTime hasta,
        OffsetDateTime apelableHasta,
        ResultadoDeApelacion resultadoApelacion) {

    public enum TipoDeSancion {
        ADVERTENCIA("Recibiste una advertencia en The Nexus Battles VI"),
        SUSPENSION("Tu cuenta de The Nexus Battles VI está suspendida"),
        BANEO("Tu cuenta de The Nexus Battles VI fue baneada de forma definitiva"),
        APELACION_RESUELTA("Resolución de tu apelación en The Nexus Battles VI");

        private final String asunto;

        TipoDeSancion(String asunto) {
            this.asunto = asunto;
        }

        public String asunto() {
            return asunto;
        }
    }

    public enum ResultadoDeApelacion {
        MANTENIDA,
        REDUCIDA,
        REVERTIDA
    }

    public CorreoPedido aCorreo() {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("apodo", apodo);
        datos.put("tipo", tipo.name());
        datos.put("motivo", motivo.trim());
        datos.put("hasta", FechaLegible.de(hasta));
        datos.put("apelableHasta", FechaLegible.de(apelableHasta));
        datos.put("resultadoApelacion", resultadoApelacion == null ? null : resultadoApelacion.name());
        return CorreoPedido.paraEnviar(Plantilla.SANCION, email, tipo.asunto(), datos);
    }
}
