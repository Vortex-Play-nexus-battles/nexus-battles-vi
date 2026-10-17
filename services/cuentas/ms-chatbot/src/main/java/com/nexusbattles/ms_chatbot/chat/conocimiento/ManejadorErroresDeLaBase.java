package com.nexusbattles.ms_chatbot.chat.conocimiento;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Regla 4 (problem details) para el panel de la base de conocimiento
// (ms-chatbot.yaml 1.6.0), con `motivo` como los errores del chat y de los
// tickets: el panel decide por el motivo, no por el texto.
//
//   409 TEMA_DUPLICADO               otro tema de la version ya tiene ese titulo
//   413 IMPORTACION_DEMASIADO_GRANDE  mas de 500 temas en el archivo (el de mas
//                                     de 8 MB lo corta LimiteDelArchivoDeImportacion
//                                     antes de llegar aqui, con el mismo cuerpo)
//   409 CAMBIO_CONCURRENTE           otra transicion de versiones gano la carrera
//
// CAMBIO_CONCURRENTE tambien cubre los fallos de concurrencia de la base
// (ConcurrencyFailureException): la revision leida ya no esta (bloqueo
// optimista, V12), un bloqueo que no se obtuvo o un abrazo mortal que
// PostgreSQL deshizo. El orden fijo de BloqueoDeVersiones los evita; si aun
// asi ocurren, la respuesta es la misma y nunca un 500. El detalle es siempre
// el texto fijo: el de la excepcion lleva SQL y nombres de entidades.
@RestControllerAdvice(basePackages = "com.nexusbattles.ms_chatbot.chat.conocimiento")
public class ManejadorErroresDeLaBase {

    static final String TEMA_DUPLICADO = "TEMA_DUPLICADO";
    static final String IMPORTACION_DEMASIADO_GRANDE = "IMPORTACION_DEMASIADO_GRANDE";
    static final String CAMBIO_CONCURRENTE = "CAMBIO_CONCURRENTE";
    static final String TITULO_ARCHIVO_GRANDE = "Archivo demasiado grande";

    @ExceptionHandler(TemaDuplicadoException.class)
    public ResponseEntity<ProblemDetail> temaDuplicado(TemaDuplicadoException ex) {
        return respuesta(HttpStatus.CONFLICT, "Tema duplicado", ex.getMessage(), TEMA_DUPLICADO);
    }

    @ExceptionHandler(ImportacionDemasiadoGrandeException.class)
    public ResponseEntity<ProblemDetail> importacionDemasiadoGrande(ImportacionDemasiadoGrandeException ex) {
        return respuesta(HttpStatus.CONTENT_TOO_LARGE, TITULO_ARCHIVO_GRANDE, ex.getMessage(),
            IMPORTACION_DEMASIADO_GRANDE);
    }

    @ExceptionHandler({CambioConcurrenteException.class, ConcurrencyFailureException.class})
    public ResponseEntity<ProblemDetail> cambioConcurrente(RuntimeException ex) {
        return respuesta(HttpStatus.CONFLICT, "Cambio simultaneo", CambioConcurrenteException.MENSAJE,
            CAMBIO_CONCURRENTE);
    }

    private static ResponseEntity<ProblemDetail> respuesta(HttpStatus estado, String titulo, String detalle,
                                                          String motivo) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(titulo);
        problema.setProperty("motivo", motivo);
        return ResponseEntity.status(estado).body(problema);
    }
}
