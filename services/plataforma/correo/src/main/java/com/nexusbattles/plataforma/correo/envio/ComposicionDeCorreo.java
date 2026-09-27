package com.nexusbattles.plataforma.correo.envio;

import com.nexusbattles.plataforma.correo.template.Plantilla;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Las variables con las que se pinta una plantilla: los datos guardados en la
 * cola mas lo que se calcula en el momento de entregar.
 *
 * <p>Hoy lo unico calculado es el {@code enlace} de los dos correos con codigo
 * (ver {@link EnlacesDeCorreo} para por que no se guarda). Sin codigo no hay
 * enlace: la plantilla oculta el boton en vez de pintar uno que no sirve.
 */
@Component
public class ComposicionDeCorreo {

    static final String ENLACE = "enlace";

    private final EnlacesDeCorreo enlaces;

    public ComposicionDeCorreo(EnlacesDeCorreo enlaces) {
        this.enlaces = enlaces;
    }

    public Map<String, Object> variables(Plantilla plantilla, String destinatario, Map<String, Object> datos) {
        Map<String, Object> variables = datos == null ? new LinkedHashMap<>() : new LinkedHashMap<>(datos);
        Object codigo = variables.get("codigo");
        if (!(codigo instanceof String texto) || texto.isBlank()) {
            return variables;
        }
        switch (plantilla) {
            case CONFIRMACION_CUENTA -> variables.put(ENLACE, enlaces.confirmacionDeCuenta(
                    PropositoDeConfirmacion.desde(variables.get("proposito")), texto, destinatario));
            case RECUPERACION_CLAVE -> variables.put(ENLACE, enlaces.recuperacionDeClave(texto, destinatario));
            default -> {
                // El resto de plantillas no lleva enlace con codigo.
            }
        }
        return variables;
    }
}
