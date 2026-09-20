package nexus.combate.api;

import nexus.combate.ClienteHeroesException;
import nexus.combate.DistribucionEfectos;
import nexus.combate.HeroeNoEncontradoException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * API del motor de combate — espejo de {@code contracts/openapi/motor-combate.yaml}.
 *
 * <p>Regla 2 de plataforma: todo bajo {@code /api/v1}.
 *
 * <p><b>Sin estado.</b> Cada peticion se resuelve entera y no deja rastro. Quien
 * lleva el combate —turno, vida, ganador— es {@code salas-partidas}, que ya lo
 * persiste. Este servicio responde una sola pregunta, la unica que solo el sabe
 * contestar: cuanto dano hace este ataque.
 *
 * <p>Vive en {@code nexus.combate.api} y no en {@code nexus.combate}: el
 * dominio sigue siendo Java puro, sin una sola anotacion, y hay una prueba que
 * lo vigila.
 */
@RestController
@RequestMapping("/api/v1/combate")
public class CombateController {

    private final ResolverAtaque resolverAtaque;

    public CombateController(ResolverAtaque resolverAtaque) {
        this.resolverAtaque = resolverAtaque;
    }

    /**
     * Resuelve un ataque: estadisticas del heroe, tirada de dados y efecto.
     *
     * <p>Un ataque que no supera la defensa tambien es 200, con
     * {@code SIN_EFECTO} y dano cero: fallar es un resultado legitimo del
     * combate, no un error de la peticion.
     */
    @PostMapping("/ataques")
    public RespuestaDeAtaque resolver(@RequestBody(required = false) PeticionDeAtaque peticion) {
        return resolverAtaque.ejecutar(peticion);
    }

    /**
     * Los seis prototipos de distribucion que define el dominio.
     *
     * <p>Evita que cada cliente se copie los porcentajes y deje de cuadrar el
     * dia que cambien.
     */
    @GetMapping("/distribuciones")
    public List<PrototipoResponse> distribuciones() {
        return PeticionDeAtaque.nombresDePrototipos().stream()
                .map(nombre -> PrototipoResponse.de(nombre,
                        PeticionDeAtaque.prototipoPorNombre(nombre)))
                .toList();
    }

    /** Espejo del esquema {@code PrototipoDeDistribucion}. */
    public record PrototipoResponse(String nombre, Distribucion distribucion) {

        static PrototipoResponse de(String nombre, DistribucionEfectos reparto) {
            return new PrototipoResponse(nombre, new Distribucion(
                    reparto.causarDano(), reparto.causarDanoCritico(), reparto.evadirElGolpe(),
                    reparto.resistirElGolpe(), reparto.escaparAlGolpe(), reparto.sinEfecto()));
        }

        /** Los seis porcentajes, sin el prototipo: aqui el nombre ya va fuera. */
        public record Distribucion(int causarDano, int causarDanoCritico, int evadirElGolpe,
                                   int resistirElGolpe, int escaparAlGolpe, int sinEfecto) {
        }
    }

    // =====================================================================
    // Errores en formato problem details (regla 4), identico a los otros
    // servicios de la plataforma.
    // =====================================================================

    @ExceptionHandler(PeticionInvalida.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail peticionInvalida(PeticionInvalida error) {
        return problema(HttpStatus.BAD_REQUEST, "peticion-invalida",
                "La peticion no se puede interpretar", error.getMessage());
    }

    /**
     * El dominio valida sus propias cotas —la distribucion suma 100, la defensa
     * no es negativa— y las rechaza con {@code IllegalArgumentException}. Es un
     * 400: lo manda mal quien llama, no falla el servicio.
     */
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

    @ExceptionHandler(HeroeSinAtaque.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ProblemDetail heroeSinAtaque(HeroeSinAtaque error) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, "heroe-sin-ataque",
                "Este heroe no ataca", error.getMessage());
    }

    /**
     * El catalogo de heroes no responde.
     *
     * <p>503 y no un resultado inventado: un combate decidido con estadisticas
     * falsas es peor que un combate que no avanza, y ademas el sintoma
     * aparecería mucho despues, en la barra de vida, sin rastro de la causa.
     */
    @ExceptionHandler(ClienteHeroesException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ProblemDetail catalogoCaido(ClienteHeroesException error) {
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "catalogo-de-heroes-no-disponible",
                "El catalogo de heroes no responde", error.getMessage());
    }

    private static ProblemDetail problema(HttpStatus estado, String tipo,
                                          String titulo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setType(URI.create("https://nexusbattles.local/errores/" + tipo));
        problema.setTitle(titulo);
        return problema;
    }
}
