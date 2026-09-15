package com.nexusbattles.ms_finanzas.seguridad;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca un método o controlador que requiere un JWT válido (cualquier rol).
 * Es más simple que {@code @RequirePermission} de ms-identidad porque
 * ms-finanzas no tiene tabla RBAC propia — la autorización fina (por rol
 * concreto) se resuelve dentro del método si hace falta, leyendo
 * {@code SecurityInterceptor.ATTR_ROL} del request. Sin esta anotación un
 * endpoint queda abierto sin autenticación.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAutenticacion {
}
