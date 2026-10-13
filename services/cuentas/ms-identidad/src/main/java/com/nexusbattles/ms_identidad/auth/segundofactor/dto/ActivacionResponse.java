package com.nexusbattles.ms_identidad.auth.segundofactor.dto;

import java.util.List;

/**
 * {@code ActivacionSegundoFactor} de ms-identidad-auth.yaml 2.2.0: los codigos
 * de recuperacion, que no se vuelven a mostrar.
 */
public record ActivacionResponse(boolean activo, List<String> codigosRecuperacion) {

    @Override
    public String toString() {
        return "ActivacionResponse[activo=" + activo + ", codigosRecuperacion="
                + (codigosRecuperacion == null ? 0 : codigosRecuperacion.size()) + "]";
    }
}
