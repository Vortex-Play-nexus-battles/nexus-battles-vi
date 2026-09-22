package com.nexusbattles.plataforma.salaspartidas.api;

import com.nexusbattles.comun.error.ErrorDeCampo;
import com.nexusbattles.comun.error.ErrorDeNegocio;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

/**
 * Traduce los errores de negocio a problem details (RFC 7807).
 *
 * <p><b>Regla 4 de plataforma:</b> formato de error estandar, identico en los 20
 * modulos. La correspondencia con la interfaz —que campo va a que parte del
 * componente Aviso— esta en {@code shared/ui-kit/MAPEO-ERRORES.md}.
 *
 * <p>Este manejador no conoce ningun error concreto: trabaja contra
 * {@link ErrorDeNegocio}, asi que cualquier error nuevo del dominio sale bien
 * formado sin tocar esta clase.
 *
 * <p><b>El orden no es decorativo.</b> Con {@code spring.mvc.problemdetails.enabled}
 * activo, Spring Boot registra su propio manejador con {@code @Order(0)}. Sin
 * declarar precedencia, este se queda con la mas baja y el de Spring gana: los
 * errores salen con {@code type: about:blank}, que el serializador ademas omite
 * por ser el valor por defecto. La interfaz decide por {@code type}, asi que sin
 * esta anotacion se queda sin nada sobre lo que decidir.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ManejadorDeErrores {

    @ExceptionHandler(ErrorDeNegocio.class)
    ProblemDetail errorDeNegocio(ErrorDeNegocio error) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.valueOf(error.estado()), error.detalle());
        problema.setType(error.tipo());
        problema.setTitle(error.titulo());

        if (!error.errores().isEmpty()) {
            problema.setProperty("errores", porCampo(error.errores()));
        }
        return problema;
    }

    /**
     * Un cuerpo que no se puede leer —un enumerado inexistente, un numero donde
     * iba texto— es culpa de quien llama, no del servidor. Sale como 400 con el
     * mismo formato que el resto, para que la interfaz no tenga dos caminos.
     */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    ProblemDetail cuerpoIlegible(Exception error) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "No se pudo leer la peticion. Revisa que los valores tengan el formato del contrato.");
        problema.setType(java.net.URI.create("https://nexusbattles.local/errores/peticion-ilegible"));
        problema.setTitle("Peticion mal formada");
        return problema;
    }

    private static List<Map<String, String>> porCampo(List<ErrorDeCampo> errores) {
        return errores.stream()
                .map(e -> Map.of("campo", e.campo(), "mensaje", e.mensaje()))
                .toList();
    }
}
