package com.nexusbattles.plataforma.correo.api;

import com.nexusbattles.plataforma.correo.cola.CorreoPedido;
import com.nexusbattles.plataforma.correo.template.Plantilla;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Correo de un hito de torneo (contrato 1.5.0, B10, {@code CorreoTorneoRequest}):
 * inscripcion confirmada, inicio, cancelacion con devolucion y premio entregado
 * (HU-TOR-002 CA-01, HU-TOR-001 CA-04, HU-TOR-007 CA-04).
 *
 * <p>Mismo criterio que CorreoSubastaRequest: torneos decide el asunto y el
 * mensaje, porque solo el conoce el hito; este servicio solo los pone sobre la
 * plantilla corporativa, como texto. Lo que cambia respecto a la subasta:
 *
 * <ul>
 *   <li><b>Sin preferencia del jugador.</b> El contrato no trae
 *       {@code debeEnviarCorreo}: el hito siempre se comunica, asi que el
 *       correo se acepta siempre como PENDIENTE y quien llama no puede pedir
 *       que se guarde como OMITIDO.</li>
 *   <li><b>Topes de largo</b> (200 el asunto, 2000 el mensaje): un asunto
 *       desmedido lo cortan o lo marcan como no deseado los clientes de
 *       correo, y la fila de la cola no es un almacen de textos.</li>
 *   <li><b>{@code torneoId}</b>, opcional, es la referencia del envio: queda
 *       en los datos de la fila (evidencia de a que torneo se referia) y se
 *       muestra al pie del mensaje, como la orden en la confirmacion de
 *       compra.</li>
 * </ul>
 *
 * <p>Un {@code torneoId} que no es un UUID no llega aqui: Jackson lo rechaza
 * al leer el cuerpo y la respuesta es 400, igual que una fecha mal escrita en
 * la sancion.
 */
public record CorreoTorneoRequest(
        @NotBlank @Email String email,
        @NotBlank String apodo,
        @NotBlank @Size(max = LARGO_MAXIMO_ASUNTO) String asunto,
        @NotBlank @Size(max = LARGO_MAXIMO_MENSAJE) String mensaje,
        UUID torneoId) {

    /** maxLength de {@code asunto} en el contrato 1.5.0. */
    static final int LARGO_MAXIMO_ASUNTO = 200;

    /** maxLength de {@code mensaje} en el contrato 1.5.0. */
    static final int LARGO_MAXIMO_MENSAJE = 2000;

    public CorreoPedido aCorreo() {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("apodo", apodo);
        datos.put("asunto", asunto);
        datos.put("mensaje", mensaje);
        // CorreoPedido descarta los nulos: sin torneoId no hay referencia que pintar.
        datos.put("torneoId", torneoId == null ? null : torneoId.toString());
        return CorreoPedido.paraEnviar(Plantilla.TORNEO, email, asunto, datos);
    }
}
