package com.nexusbattles.ms_identidad.auth.codigos;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.net.URI;

/**
 * Problem details (regla 4) de las rutas de B1, con el {@code type} estable
 * que ya usa este servicio ({@code https://nexusbattles.upb.edu.co/errors/...}).
 * La interfaz decide por el {@code type}, nunca por la redaccion.
 */
public final class Problemas {

    public static final String BASE = "https://nexusbattles.upb.edu.co/errors/";

    private Problemas() {
    }

    public static ResponseEntity<ProblemDetail> de(HttpStatus estado, String tipo, String titulo, String detalle,
                                                   String ruta) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setType(URI.create(BASE + tipo));
        problema.setTitle(titulo);
        if (ruta != null) {
            problema.setInstance(URI.create(ruta));
        }
        return ResponseEntity.status(estado).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problema);
    }

    public static ResponseEntity<ProblemDetail> codigoInvalido(String ruta) {
        return de(HttpStatus.BAD_REQUEST, "codigo-invalido", "Código inválido", CodigoInvalidoException.MENSAJE, ruta);
    }

    public static ResponseEntity<ProblemDetail> demasiadosIntentos(String ruta) {
        return de(HttpStatus.TOO_MANY_REQUESTS, "demasiados-intentos", "Demasiados intentos",
                DemasiadosIntentosException.MENSAJE, ruta);
    }

    public static ResponseEntity<ProblemDetail> datosInvalidos(String ruta) {
        return de(HttpStatus.BAD_REQUEST, "datos-invalidos", "Datos inválidos",
                "La petición no tiene la forma esperada: revisa los campos obligatorios.", ruta);
    }
}
