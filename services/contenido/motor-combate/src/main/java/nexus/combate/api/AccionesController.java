package nexus.combate.api;

import nexus.combate.ClienteHeroesException;
import nexus.combate.HeroeNoEncontradoException;
import nexus.combate.reglas.AccionNoPermitida;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * El combate de verdad — {@code motor-combate.yaml} 1.2.0.
 *
 * <p>{@code POST /api/v1/combate/acciones} resuelve una accion del turno y
 * {@code POST /api/v1/combate/turnos} lo que pasa al empezar uno. Sin estado:
 * el estado de los combatientes llega entero y sale entero; quien lo guarda es
 * {@code salas-partidas}. Las dos rutas son exclusivas de servicio
 * ({@code SeguridadConfig}): el cliente del jugador elige accion y objetivo en
 * salas, y es salas quien pregunta aqui.
 */
@RestController
@RequestMapping("/api/v1/combate")
public class AccionesController {

    private final ServicioDeCombate servicio;

    public AccionesController(ServicioDeCombate servicio) {
        this.servicio = servicio;
    }

    /** Una accion del turno. Fallar el golpe tambien es un 200. */
    @PostMapping("/acciones")
    public RespuestaDeAccion resolver(@RequestBody(required = false) PeticionDeAccion peticion) {
        return servicio.resolver(peticion);
    }

    /** El comienzo del turno de un combatiente: protecciones, efectos por turno y +2 de poder. */
    @PostMapping("/turnos")
    public RespuestaDeTurno iniciarTurno(@RequestBody(required = false) PeticionDeTurno peticion) {
        return servicio.iniciarTurno(peticion);
    }

    // =====================================================================
    // Errores en formato problem details (regla 4).
    // =====================================================================

    /**
     * La accion no se puede jugar ahora: en carga, bloqueada por nivel, contra
     * un companero... 409 con {@code motivo}, y no se aplico nada.
     */
    @ExceptionHandler(AccionNoPermitida.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ProblemDetail accionNoPermitida(AccionNoPermitida error) {
        ProblemDetail problema = problema(HttpStatus.CONFLICT, "accion-no-permitida",
                "La accion no se puede jugar ahora", error.getMessage());
        problema.setProperty("motivo", error.motivo().name());
        return problema;
    }

    @ExceptionHandler(PeticionInvalida.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail peticionInvalida(PeticionInvalida error) {
        return problema(HttpStatus.BAD_REQUEST, "peticion-invalida",
                "La peticion no se puede interpretar", error.getMessage());
    }

    /** Un JSON que no se puede leer (un tipo de efecto que no existe, un numero con letras). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail jsonIlegible(HttpMessageNotReadableException error) {
        return problema(HttpStatus.BAD_REQUEST, "peticion-invalida",
                "La peticion no se puede interpretar",
                "El cuerpo no es un JSON valido para este contrato.");
    }

    /** Las cotas del dominio (nivel de 1 a 8, vida no negativa, ids repetidos...). */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail reglaDelDominio(IllegalArgumentException error) {
        return problema(HttpStatus.BAD_REQUEST, "peticion-invalida",
                "La peticion no cumple las reglas del combate", error.getMessage());
    }

    @ExceptionHandler(HeroeNoEncontradoException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ProblemDetail heroeNoEncontrado(HeroeNoEncontradoException error) {
        return problema(HttpStatus.NOT_FOUND, "heroe-no-encontrado",
                "El heroe no existe", error.getMessage());
    }

    /** El catalogo de heroes no responde: no se inventa un combatiente. */
    @ExceptionHandler(ClienteHeroesException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ProblemDetail catalogoCaido(ClienteHeroesException error) {
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "catalogo-de-heroes-no-disponible",
                "El catalogo de heroes no responde", error.getMessage());
    }

    private static ProblemDetail problema(HttpStatus estado, String tipo, String titulo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setType(URI.create("https://nexusbattles.local/errores/" + tipo));
        problema.setTitle(titulo);
        return problema;
    }
}
