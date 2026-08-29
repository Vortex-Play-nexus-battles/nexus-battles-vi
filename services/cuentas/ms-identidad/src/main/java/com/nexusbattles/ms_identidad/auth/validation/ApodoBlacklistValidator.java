package com.nexusbattles.ms_identidad.auth.validation;

import com.nexusbattles.ms_identidad.auth.validation.dto.ListaNegraResponse;
import org.springframework.stereotype.Component;

@Component
public class ApodoBlacklistValidator {

    private final ListaNegraClient listaNegraClient;

    public ApodoBlacklistValidator(ListaNegraClient listaNegraClient) {
        this.listaNegraClient = listaNegraClient;
    }

    public void validar(String apodo) {
        if (apodo == null) return;

        ListaNegraResponse respuesta = listaNegraClient.verificar(apodo);

        if (!respuesta.isAprobado()) {
            String motivo = respuesta.getMotivo() != null
                ? respuesta.getMotivo()
                : "El apodo contiene términos prohibidos por la política de la comunidad.";
            throw new IllegalArgumentException(motivo);
        }
    }
}
