package nexus.inventario.aplicacion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import nexus.inventario.dominio.TablaDeNiveles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * {@link FuenteDeTablaDeNiveles} contra heroes: {@code GET
 * /api/v1/progresion/niveles}, publico (heroes.yaml 1.1.0), asi que no hace
 * falta credencial. Mismo patron que {@link ResolutorDeEstadisticasHeroeHttp}:
 * {@code HttpClient} plano con tiempo de espera y records tolerantes.
 *
 * <p>La tabla es un dato del producto, igual para todos: se guarda en memoria
 * tras la primera lectura buena. Un fallo no se guarda, se reintenta en la
 * siguiente liberacion.
 */
@Component
public class TablaDeNivelesHttp implements FuenteDeTablaDeNiveles {

    private final URI base;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private volatile TablaDeNiveles guardada;

    @Autowired
    public TablaDeNivelesHttp(@Value("${heroes.base-url}") String base) {
        this(URI.create(base), HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build());
    }

    public TablaDeNivelesHttp(URI base, HttpClient http) {
        this.base = base;
        this.http = http;
    }

    @Override
    public TablaDeNiveles tabla() {
        TablaDeNiveles actual = guardada;
        if (actual != null) {
            return actual;
        }
        HttpRequest peticion = HttpRequest.newBuilder(base.resolve("/api/v1/progresion/niveles"))
                .GET()
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(5))
                .build();
        HttpResponse<String> respuesta;
        try {
            respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ProgresionNoDisponibleException("No se pudo contactar al servicio de heroes", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProgresionNoDisponibleException("Consulta a heroes interrumpida", e);
        }
        if (respuesta.statusCode() != 200) {
            throw new ProgresionNoDisponibleException(
                    "Heroes respondio " + respuesta.statusCode() + " a la tabla de niveles", null);
        }
        try {
            List<NivelJson> niveles = json.readValue(respuesta.body(), new TypeReference<List<NivelJson>>() {
            });
            List<Double> paraSubir = niveles.stream()
                    .sorted(Comparator.comparingInt(NivelJson::nivel))
                    .filter(n -> n.experienciaParaSubir() != null)
                    .map(NivelJson::experienciaParaSubir)
                    .toList();
            TablaDeNiveles tabla = new TablaDeNiveles(paraSubir);
            guardada = tabla;
            return tabla;
        } catch (IOException | IllegalArgumentException e) {
            throw new ProgresionNoDisponibleException("La tabla de niveles de heroes no se pudo interpretar", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NivelJson(int nivel, Double experienciaParaSubir) {
    }
}
