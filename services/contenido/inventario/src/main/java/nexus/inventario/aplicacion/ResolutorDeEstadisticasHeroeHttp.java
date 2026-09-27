package nexus.inventario.aplicacion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import nexus.inventario.dominio.EstadisticasHeroe;
import nexus.inventario.dominio.FormulaDetalle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Adaptador HTTP real de {@link ResolutorDeEstadisticasHeroe}: consume
 * {@code GET /api/v1/heroes/{nombre}/niveles/{nivel}} (heroes.yaml) y lee sus
 * {@code estadisticas}, ya escaladas al nivel del heroe. Hasta B4 leia la
 * ficha ({@code GET /api/v1/heroes/{nombre}}, {@code estadisticasNivel1}) y
 * todo heroe salia en nivel 1: el inventario no guardaba nivel. Ahora cada
 * heroe tiene el suyo y el escalado lo hace quien es dueno de la regla.
 * Mismo patron que ClienteHeroesHttp en motor-combate:
 * HttpClient plano con timeout, URI armada con el constructor de 5
 * argumentos (nunca URLEncoder, que rompe con espacios en el nombre del
 * prototipo) y deserializacion con records privados
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)}.
 *
 * <p>A diferencia de ClienteHeroesHttp (que solo necesita defensa y
 * ataqueDetalle para el motor de combate), este adaptador mapea el esquema
 * Estadisticas COMPLETO: poder, vida, defensa, ataqueDetalle, danoDetalle y
 * sanarDetalle. heroes usa {@code spring.jackson.default-property-inclusion
 * =non-null}, asi que esos tres ultimos campos vienen AUSENTES (no
 * presentes con valor null) cuando el prototipo no aplica — ver
 * HeroesController.EstadisticasVista y Estadisticas.java en el servicio
 * heroes. Jackson deja el campo del record en null cuando la clave falta
 * del JSON, asi que no hace falta ningun manejo especial mas alla de
 * propagar ese null a FormulaDetalle.
 */
@Component
public class ResolutorDeEstadisticasHeroeHttp implements ResolutorDeEstadisticasHeroe {

    private final URI baseUri;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public ResolutorDeEstadisticasHeroeHttp(@Value("${heroes.base-url}") String baseUrl) {
        this(URI.create(baseUrl), HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    public ResolutorDeEstadisticasHeroeHttp(URI baseUri, HttpClient httpClient) {
        this.baseUri = baseUri;
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public EstadisticasHeroe resolver(String prototipo, int nivel) {
        URI uri = construirUriDeHeroe(prototipo, nivel);

        HttpRequest peticion = HttpRequest.newBuilder(uri)
                .GET()
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(5))
                .build();

        HttpResponse<String> respuesta;
        try {
            respuesta = httpClient.send(peticion, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ResolutorDeEstadisticasHeroeException(
                    "No se pudo contactar al servicio de heroes para '" + prototipo + "'", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResolutorDeEstadisticasHeroeException(
                    "Consulta a heroes interrumpida para '" + prototipo + "'", e);
        }

        if (respuesta.statusCode() == 404) {
            throw new PrototipoDeHeroeNoEncontradoException(prototipo, extraerDetalleDeProblema(respuesta.body()));
        }

        if (respuesta.statusCode() != 200) {
            throw new ResolutorDeEstadisticasHeroeException(
                    "Respuesta inesperada (" + respuesta.statusCode() + ") al consultar '" + prototipo + "'");
        }

        return parsearFicha(respuesta.body());
    }

    private URI construirUriDeHeroe(String prototipo, int nivel) {
        try {
            return new URI(
                    baseUri.getScheme(),
                    baseUri.getAuthority(),
                    "/api/v1/heroes/" + prototipo + "/niveles/" + nivel,
                    null,
                    null);
        } catch (URISyntaxException e) {
            throw new ResolutorDeEstadisticasHeroeException(
                    "Nombre de prototipo invalido para construir la URL: '" + prototipo + "'", e);
        }
    }

    private EstadisticasHeroe parsearFicha(String cuerpoJson) {
        try {
            VistaPorNivelJson vista = objectMapper.readValue(cuerpoJson, VistaPorNivelJson.class);
            EstadisticasJson estadisticas = vista.estadisticas();
            if (estadisticas == null) {
                throw new ResolutorDeEstadisticasHeroeException("La vista por nivel de heroes no trae estadisticas");
            }

            return new EstadisticasHeroe(
                    estadisticas.poder(),
                    estadisticas.vida(),
                    estadisticas.defensa(),
                    aFormulaDetalle(estadisticas.ataqueDetalle()),
                    aFormulaDetalle(estadisticas.danoDetalle()),
                    aFormulaDetalle(estadisticas.sanarDetalle()));
        } catch (IOException e) {
            throw new ResolutorDeEstadisticasHeroeException("Respuesta de heroes no se pudo interpretar: " + e.getMessage(), e);
        }
    }

    private FormulaDetalle aFormulaDetalle(FormulaDetalleJson formula) {
        return formula == null ? null : new FormulaDetalle(formula.base(), formula.cantidadDados(), formula.caras());
    }

    private String extraerDetalleDeProblema(String cuerpoJson) {
        try {
            ProblemaJson problema = objectMapper.readValue(cuerpoJson, ProblemaJson.class);
            return problema.detail() != null ? problema.detail() : "Prototipo de heroe no disponible";
        } catch (IOException e) {
            return "Prototipo de heroe no disponible";
        }
    }

    /** {@code VistaPorNivel} de heroes.yaml: solo interesan las estadisticas ya escaladas. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record VistaPorNivelJson(EstadisticasJson estadisticas) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EstadisticasJson(
            int poder,
            int vida,
            int defensa,
            FormulaDetalleJson ataqueDetalle,
            FormulaDetalleJson danoDetalle,
            FormulaDetalleJson sanarDetalle) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record FormulaDetalleJson(int base, int cantidadDados, int caras) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProblemaJson(String detail) {
    }
}
