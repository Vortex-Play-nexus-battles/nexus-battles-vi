package com.nexusbattles.ms_identidad.auth.segundofactor.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nexusbattles.ms_identidad.auth.dto.LoginResponse;

import java.util.List;

/**
 * {@code LoginConSegundoFactorResponse} de ms-identidad-auth.yaml 2.2.0: la
 * {@link LoginResponse} de siempre —el mismo cuerpo que ya sabe leer quien
 * entra— mas lo que solo tiene sentido tras el segundo factor. Los dos campos
 * extra solo salen cuando aplican.
 */
public class AccesoConSegundoFactorResponse extends LoginResponse {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Long codigosRecuperacionRestantes;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final List<String> codigosRecuperacion;

    private AccesoConSegundoFactorResponse(LoginResponse base, Long codigosRecuperacionRestantes,
                                           List<String> codigosRecuperacion) {
        super(base.getUsuarioId(), base.getApodo(), base.getEmail(), base.getRol(), base.isDispositivoNuevo(),
                base.getToken(), base.getUid(), base.isOnboardingListo());
        this.codigosRecuperacionRestantes = codigosRecuperacionRestantes;
        this.codigosRecuperacion = codigosRecuperacion;
    }

    /** Se entro con el codigo de la aplicacion. */
    public static AccesoConSegundoFactorResponse con(LoginResponse base) {
        return new AccesoConSegundoFactorResponse(base, null, null);
    }

    /** Se entro con un codigo de recuperacion: cuantos quedan. */
    public static AccesoConSegundoFactorResponse conRecuperacion(LoginResponse base, long restantes) {
        return new AccesoConSegundoFactorResponse(base, restantes, null);
    }

    /** Enrolamiento obligatorio recien confirmado: los codigos, una sola vez. */
    public static AccesoConSegundoFactorResponse recienActivado(LoginResponse base, List<String> codigos) {
        return new AccesoConSegundoFactorResponse(base, null, List.copyOf(codigos));
    }

    public Long getCodigosRecuperacionRestantes() {
        return codigosRecuperacionRestantes;
    }

    public List<String> getCodigosRecuperacion() {
        return codigosRecuperacion;
    }
}
