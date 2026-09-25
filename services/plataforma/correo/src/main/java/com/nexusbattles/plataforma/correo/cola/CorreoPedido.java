package com.nexusbattles.plataforma.correo.cola;

import com.nexusbattles.plataforma.correo.template.Plantilla;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Lo que una peticion de la API pide encolar: que plantilla, para quien, con
 * que asunto y con que datos.
 *
 * <p>Lo arma cada {@code Correo*Request} a partir de su cuerpo, que es quien
 * conoce la forma de su correo; la cola no sabe de ningun correo en concreto.
 *
 * @param plantilla        el correo
 * @param destinatario     direccion completa (solo la ve el trabajador)
 * @param asunto           asunto ya decidido
 * @param datos            variables de la plantilla; los nulos se descartan
 * @param motivoDeOmision  si no es nulo, el correo se guarda como OMITIDO y
 *                         no se envia (p. ej. {@code debeEnviarCorreo=false})
 */
public record CorreoPedido(
        Plantilla plantilla,
        String destinatario,
        String asunto,
        Map<String, Object> datos,
        String motivoDeOmision) {

    public CorreoPedido {
        Objects.requireNonNull(plantilla, "plantilla");
        Objects.requireNonNull(destinatario, "destinatario");
        Objects.requireNonNull(asunto, "asunto");
        Map<String, Object> copia = new LinkedHashMap<>();
        if (datos != null) {
            datos.forEach((clave, valor) -> {
                if (valor != null) {
                    copia.put(clave, valor);
                }
            });
        }
        datos = Collections.unmodifiableMap(copia);
    }

    /** Un correo que hay que entregar. */
    public static CorreoPedido paraEnviar(
            Plantilla plantilla, String destinatario, String asunto, Map<String, Object> datos) {
        return new CorreoPedido(plantilla, destinatario, asunto, datos, null);
    }

    /**
     * Un correo que quien llama decidio no enviar. Se guarda igual, sin datos,
     * para que la evidencia de entrega pueda decir por que no salio.
     */
    public static CorreoPedido suprimido(Plantilla plantilla, String destinatario, String asunto, String motivo) {
        return new CorreoPedido(plantilla, destinatario, asunto, Map.of(), Objects.requireNonNull(motivo));
    }

    public boolean debeEnviarse() {
        return motivoDeOmision == null;
    }
}
