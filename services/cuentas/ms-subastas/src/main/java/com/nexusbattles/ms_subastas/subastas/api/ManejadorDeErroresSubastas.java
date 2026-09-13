package com.nexusbattles.ms_subastas.subastas.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * HU-SUB-011. Pule los mensajes de error que Spring ya genera automaticamente
 * (spring.mvc.problem-details.enabled=true), sin cambiar el formato
 * application/problem+json ni el status code -- solo el texto del "detail".
 *
 * basePackages acotado a subastas.api a proposito: no debe afectar los
 * controladores de pujas (Andres) cuando existan.
 *
 * @Order(HIGHEST_PRECEDENCE) es necesario: Spring Boot registra su propio
 * manejador interno cuando problem-details esta habilitado, y sin prioridad
 * explicita ese manejador interno se ejecuta antes que este, sin error
 * visible -- solo el titulo generico "Bad Request" seguia apareciendo.
 */
@RestControllerAdvice(basePackages = "com.nexusbattles.ms_subastas.subastas.api")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ManejadorDeErroresSubastas {

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail manejarParametroInvalido(MethodArgumentTypeMismatchException ex,
                                                  HttpServletRequest request) {
        ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problema.setType(URI.create("https://nexusbattles.upb.edu.co/errors/parametro-invalido"));
        problema.setTitle("Parámetro inválido");

        Class<?> tipoEnum = resolverTipoEnumSubyacente(ex);
        String detalle;
        if (tipoEnum != null) {
            String valoresPermitidos = Arrays.stream(tipoEnum.getEnumConstants())
                .map(Object::toString)
                .collect(Collectors.joining(", "));
            detalle = "El filtro '%s' no reconoce el valor '%s'. Valores permitidos: %s."
                .formatted(ex.getName(), ex.getValue(), valoresPermitidos);
        } else {
            detalle = "El parámetro '%s' no tiene un valor válido: '%s'."
                .formatted(ex.getName(), ex.getValue());
        }

        problema.setDetail(detalle);
        problema.setInstance(URI.create(request.getRequestURI()));
        return problema;
    }

    /**
     * ex.getRequiredType() da el tipo del elemento directo (ej. TipoProducto)
     * cuando el parametro es un enum simple, pero da el tipo del contenedor
     * (List) -- no del elemento -- cuando el parametro es List<TipoProducto>,
     * por el borrado de tipos genericos de Java. Para ese caso, se resuelve
     * el tipo real del elemento desde el tipo generico declarado del
     * parametro del metodo (MethodParameter), no desde la excepcion misma.
     */
    private Class<?> resolverTipoEnumSubyacente(MethodArgumentTypeMismatchException ex) {
        Class<?> tipoRequerido = ex.getRequiredType();
        if (tipoRequerido != null && tipoRequerido.isEnum()) {
            return tipoRequerido;
        }

        MethodParameter parametro = ex.getParameter();
        if (parametro != null) {
            Type tipoGenerico = parametro.getGenericParameterType();
            if (tipoGenerico instanceof ParameterizedType parametrizado) {
                Type[] argumentos = parametrizado.getActualTypeArguments();
                if (argumentos.length == 1 && argumentos[0] instanceof Class<?> elemento && elemento.isEnum()) {
                    return elemento;
                }
            }
        }
        return null;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail manejarParametroFaltante(MissingServletRequestParameterException ex,
                                                  HttpServletRequest request) {
        ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problema.setType(URI.create("https://nexusbattles.upb.edu.co/errors/parametro-faltante"));
        problema.setTitle("Parámetro obligatorio faltante");
        problema.setDetail("Falta el parámetro obligatorio '%s' (%s)."
            .formatted(ex.getParameterName(), ex.getParameterType()));
        problema.setInstance(URI.create(request.getRequestURI()));
        return problema;
    }
}
