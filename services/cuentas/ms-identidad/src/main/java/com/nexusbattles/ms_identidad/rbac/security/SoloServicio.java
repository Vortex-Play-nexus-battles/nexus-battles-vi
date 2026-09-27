package com.nexusbattles.ms_identidad.rbac.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Ruta interna que solo atiende a otros servicios (B2): token
 * {@code client_credentials} firmado por este mismo servicio con
 * {@code rol=SERVICIO} ({@link InterceptorDeServicio}).
 *
 * <p>{@link #azp()} restringe ademas QUE servicio: vacio = cualquiera con
 * credencial de servicio.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SoloServicio {

    /** Servicios ({@code client_id}, claim {@code azp}) que pueden llamar; vacio = cualquiera. */
    String[] azp() default {};
}
