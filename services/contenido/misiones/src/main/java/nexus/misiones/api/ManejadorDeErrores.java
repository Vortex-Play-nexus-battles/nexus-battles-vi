package nexus.misiones.api;

import com.nexusbattles.plataforma.resiliencia.DependenciaDegradada;
import com.nexusbattles.plataforma.resiliencia.ErroresDeDegradacion;
import java.net.URI;
import java.util.Map;
import nexus.misiones.aplicacion.EstrategiaInvalida;
import nexus.misiones.aplicacion.EstrategiaNoGuardada;
import nexus.misiones.aplicacion.HeroeNoApto;
import nexus.misiones.aplicacion.HeroeNoEncontrado;
import nexus.misiones.aplicacion.RechazoDelServicio;
import nexus.misiones.dominio.EjecucionNoEncontrada;
import nexus.misiones.dominio.MisionNoEncontrada;
import nexus.misiones.dominio.ReglaDeMisionIncumplida;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Los errores del servicio como problem details (regla 4, RFC 9457), con el
 * {@code detail} apto para el jugador. La caida de una dependencia no pasa por
 * aqui: la traduce {@code ManejadorDeDegradacion} de plataforma-resiliencia,
 * identico en los veinte modulos.
 */
@RestControllerAdvice
class ManejadorDeErrores {

    private static final Logger BITACORA = LoggerFactory.getLogger(ManejadorDeErrores.class);
    private static final String BASE = "https://nexusbattles.local/errores/";

    /** Lo que ve el jugador si una dependencia rechaza a misiones: la seccion, no el servicio. */
    private static final Map<String, String> SECCIONES = Map.of(
            "inventario", "Inventario",
            "productos", "Catálogo de productos",
            "heroes", "Reglas de los héroes",
            "motor-combate", "Motor de combate");

    private final long reintentarEnSegundos;

    ManejadorDeErrores(@Value("${resiliencia.reintentar-en-segundos:30}") long reintentarEnSegundos) {
        this.reintentarEnSegundos = reintentarEnSegundos;
    }

    @ExceptionHandler(MisionNoEncontrada.class)
    ResponseEntity<ProblemDetail> misionNoEncontrada(MisionNoEncontrada e) {
        return problema(HttpStatus.NOT_FOUND, "mision-no-encontrada", "Misión no encontrada", e.getMessage());
    }

    @ExceptionHandler(EjecucionNoEncontrada.class)
    ResponseEntity<ProblemDetail> ejecucionNoEncontrada(EjecucionNoEncontrada e) {
        return problema(HttpStatus.NOT_FOUND, "ejecucion-no-encontrada", "Ejecución no encontrada", e.getMessage());
    }

    @ExceptionHandler(HeroeNoEncontrado.class)
    ResponseEntity<ProblemDetail> heroeNoEncontrado(HeroeNoEncontrado e) {
        return problema(HttpStatus.NOT_FOUND, "heroe-no-encontrado", "Héroe no encontrado", e.getMessage());
    }

    @ExceptionHandler(EstrategiaNoGuardada.class)
    ResponseEntity<ProblemDetail> estrategiaNoGuardada(EstrategiaNoGuardada e) {
        return problema(HttpStatus.NOT_FOUND, "estrategia-no-guardada", "Sin estrategia guardada", e.getMessage());
    }

    /** Bloqueada, en curso, sin intentos, escalon sin desbloquear, heroe ocupado, sin reporte... */
    @ExceptionHandler(ReglaDeMisionIncumplida.class)
    ResponseEntity<ProblemDetail> reglaIncumplida(ReglaDeMisionIncumplida e) {
        return problema(HttpStatus.CONFLICT, "regla-de-mision", "No se puede hacer ahora", e.getMessage());
    }

    /** HU-MIS-008: «se muestran todas» las razones, no solo la primera. */
    @ExceptionHandler(HeroeNoApto.class)
    ResponseEntity<ProblemDetail> heroeNoApto(HeroeNoApto e) {
        ResponseEntity<ProblemDetail> respuesta = problema(HttpStatus.UNPROCESSABLE_CONTENT, "heroe-no-apto",
                "El héroe no puede salir a esta misión", e.getMessage());
        respuesta.getBody().setProperty("motivos", e.motivos());
        return respuesta;
    }

    @ExceptionHandler(EstrategiaInvalida.class)
    ResponseEntity<ProblemDetail> estrategiaInvalida(EstrategiaInvalida e) {
        ResponseEntity<ProblemDetail> respuesta = problema(HttpStatus.UNPROCESSABLE_CONTENT, "estrategia-invalida",
                "La estrategia no vale para este héroe", e.getMessage());
        respuesta.getBody().setProperty("habilidadesValidas", e.habilidadesValidas());
        return respuesta;
    }

    /**
     * Una dependencia rechazo a misiones (un 4xx que no es culpa del jugador:
     * una credencial mal configurada, un prototipo que el catalogo no
     * conoce). Para el jugador es lo mismo que una caida: la seccion no esta
     * disponible. En la bitacora queda como error, porque alguien tiene que
     * arreglarlo.
     */
    @ExceptionHandler(RechazoDelServicio.class)
    ResponseEntity<ProblemDetail> rechazo(RechazoDelServicio e) {
        BITACORA.error("Una dependencia rechazo la operacion: {}", e.getMessage());
        String seccion = SECCIONES.getOrDefault(e.servicio(), "Misiones");
        ProblemDetail problema = ErroresDeDegradacion.problema(
                new DependenciaDegradada(e.servicio(), seccion, e), reintentarEnSegundos);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(reintentarEnSegundos))
                .body(problema);
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMediaTypeNotSupportedException.class,
            IllegalArgumentException.class})
    ResponseEntity<ProblemDetail> solicitudInvalida(Exception e) {
        return problema(HttpStatus.BAD_REQUEST, "solicitud-invalida", "Solicitud inválida",
                "La petición no cumple el contrato de misiones: revisa los parámetros y el cuerpo.");
    }

    private static ResponseEntity<ProblemDetail> problema(HttpStatus estado, String tipo, String titulo,
                                                          String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setType(URI.create(BASE + tipo));
        problema.setTitle(titulo);
        return ResponseEntity.status(estado).body(problema);
    }
}
