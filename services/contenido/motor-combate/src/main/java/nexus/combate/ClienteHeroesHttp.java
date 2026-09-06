package nexus.combate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class ClienteHeroesHttp implements ClienteHeroes {

    private final URI baseUri;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ClienteHeroesHttp(URI baseUri) {
        this(baseUri, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    public ClienteHeroesHttp(URI baseUri, HttpClient httpClient) {
        this.baseUri = baseUri;
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public EstadisticasHeroeRespuesta obtenerEstadisticas(String nombreHeroe) {
        URI uri = construirUriDeHeroe(nombreHeroe);

        HttpRequest peticion = HttpRequest.newBuilder(uri)
            .GET()
            .header("Accept", "application/json")
            .timeout(Duration.ofSeconds(5))
            .build();

        HttpResponse<String> respuesta;
        try {
            respuesta = httpClient.send(peticion, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ClienteHeroesException(
                "No se pudo contactar al servicio de heroes para '" + nombreHeroe + "'", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ClienteHeroesException(
                "Consulta a heroes interrumpida para '" + nombreHeroe + "'", e);
        }

        if (respuesta.statusCode() == 404) {
            throw new HeroeNoEncontradoException(nombreHeroe, extraerDetalleDeProblema(respuesta.body()));
        }

        if (respuesta.statusCode() != 200) {
            throw new ClienteHeroesException(
                "Respuesta inesperada (" + respuesta.statusCode() + ") al consultar '" + nombreHeroe + "'");
        }

        return parsearFicha(respuesta.body());
    }

    private URI construirUriDeHeroe(String nombreHeroe) {
        try {
            return new URI(
                baseUri.getScheme(),
                baseUri.getAuthority(),
                "/api/v1/heroes/" + nombreHeroe,
                null,
                null);
        } catch (URISyntaxException e) {
            throw new ClienteHeroesException("Nombre de heroe invalido para construir la URL: '" + nombreHeroe + "'", e);
        }
    }

    private EstadisticasHeroeRespuesta parsearFicha(String cuerpoJson) {
        try {
            FichaHeroeJson ficha = objectMapper.readValue(cuerpoJson, FichaHeroeJson.class);
            EstadisticasJson estadisticas = ficha.estadisticasNivel1();

            DetalleAtaque ataqueDetalle = estadisticas.ataqueDetalle() == null
                ? null
                : new DetalleAtaque(
                    estadisticas.ataqueDetalle().base(),
                    estadisticas.ataqueDetalle().cantidadDados(),
                    estadisticas.ataqueDetalle().caras());

            return new EstadisticasHeroeRespuesta(estadisticas.defensa(), ataqueDetalle);
        } catch (IOException e) {
            throw new ClienteHeroesException("Respuesta de heroes no se pudo interpretar: " + e.getMessage(), e);
        }
    }

    private String extraerDetalleDeProblema(String cuerpoJson) {
        try {
            ProblemaJson problema = objectMapper.readValue(cuerpoJson, ProblemaJson.class);
            return problema.detail() != null ? problema.detail() : "Heroe no disponible";
        } catch (IOException e) {
            return "Heroe no disponible";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record FichaHeroeJson(EstadisticasJson estadisticasNivel1) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EstadisticasJson(int defensa, FormulaDetalleJson ataqueDetalle) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record FormulaDetalleJson(int base, int cantidadDados, int caras) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProblemaJson(String detail) {
    }
}
