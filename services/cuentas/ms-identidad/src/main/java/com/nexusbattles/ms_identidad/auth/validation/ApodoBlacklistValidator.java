package com.nexusbattles.ms_identidad.auth.validation;

import com.nexusbattles.ms_identidad.auth.validation.dto.ListaNegraRequest;
import com.nexusbattles.ms_identidad.auth.validation.dto.ListaNegraResponse;
import org.springframework.stereotype.Component;

/**
 * El apodo contra la lista negra (HU-ADM-002; 7.1.1 «el apodo no podra
 * contener palabras ofensivas o nombres respetados o reconocidos...»).
 *
 * <p>Lo usan el registro, el alta administrativa y los dos cambios de apodo
 * (perfil propio y edicion de administracion). Tres desenlaces:
 * <ul>
 *   <li>permitido: vuelve sin mas;</li>
 *   <li>rechazado ({@code aprobado=false} o {@code accion=RECHAZAR}):
 *       {@link IllegalArgumentException} con el motivo, que cada flujo
 *       traduce a su 400 {@code apodo-no-permitido};</li>
 *   <li>sin respuesta (o una respuesta vacia): {@link ModeracionNoDisponibleException},
 *       503. Fail-closed desde B2.</li>
 * </ul>
 */
@Component
public class ApodoBlacklistValidator {

    static final String MOTIVO_POR_OMISION = "El apodo contiene términos prohibidos por la política de la comunidad.";

    private final ListaNegraClient listaNegraClient;

    public ApodoBlacklistValidator(ListaNegraClient listaNegraClient) {
        this.listaNegraClient = listaNegraClient;
    }

    public void validar(String apodo) {
        if (apodo == null) return;

        ListaNegraResponse respuesta = listaNegraClient.verificar(apodo, ListaNegraRequest.CONTEXTO_APODO);
        if (respuesta == null) {
            throw new ModeracionNoDisponibleException(
                "No pudimos comprobar el apodo en este momento. Inténtalo de nuevo en unos segundos.");
        }

        if (!respuesta.isAprobado() || ListaNegraResponse.RECHAZAR.equals(respuesta.getAccion())) {
            String motivo = respuesta.getMotivo() != null && !respuesta.getMotivo().isBlank()
                ? respuesta.getMotivo()
                : MOTIVO_POR_OMISION;
            throw new IllegalArgumentException(motivo);
        }
    }
}
