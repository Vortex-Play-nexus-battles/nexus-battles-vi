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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ClienteInventarioBotinHttp implements InventarioBotin {

    private static final String CABECERA_IDENTIDAD = "X-User-Name";
    private final URI baseUri;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ClienteInventarioBotinHttp(URI baseUri) {
        this(baseUri, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    public ClienteInventarioBotinHttp(URI baseUri, HttpClient httpClient) {
        this.baseUri = Objects.requireNonNull(baseUri, "La URL de inventario es obligatoria");
        this.httpClient = Objects.requireNonNull(httpClient, "El cliente HTTP es obligatorio");
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public List<ElementoCandidatoBotin> listarCandidatos(
            String propietarioEnemigoId,
            String heroeEnemigoId) {
        exigirTexto(propietarioEnemigoId, "propietarioEnemigoId");
        exigirTexto(heroeEnemigoId, "heroeEnemigoId");
        Set<String> equipados = consultarEquipados(propietarioEnemigoId, heroeEnemigoId);
        List<ElementoCandidatoBotin> candidatos = new ArrayList<>();
        int pagina = 0;
        boolean ultima;
        do {
            PaginaInventarioJson inventario = consultarPagina(propietarioEnemigoId, pagina);
            if (inventario.elementos() == null) {
                throw new IntegracionBotinException("Inventario devolvio una pagina sin elementos");
            }
            inventario.elementos().stream()
                    .map(elemento -> convertir(elemento, equipados))
                    .flatMap(java.util.Optional::stream)
                    .forEach(candidatos::add);
            ultima = inventario.ultima();
            pagina++;
            if (pagina > 1000) {
                throw new IntegracionBotinException("Inventario excedio el limite de paginacion");
            }
        } while (!ultima);
        return List.copyOf(candidatos);
    }

    @Override
    public void otorgar(String jugadorId, ElementoCandidatoBotin elemento) {
        exigirTexto(jugadorId, "jugadorId");
        Objects.requireNonNull(elemento, "El elemento es obligatorio");
        SolicitudCrearElemento solicitud = new SolicitudCrearElemento(
                elemento.productoId(),
                elemento.tipo(),
                elemento.nombrePropio(),
                elemento.parteArmadura());
        String cuerpo;
        try {
            cuerpo = objectMapper.writeValueAsString(solicitud);
        } catch (IOException excepcion) {
            throw new IntegracionBotinException("No se pudo preparar el botin para inventario", excepcion);
        }
        HttpRequest peticion = HttpRequest.newBuilder(
                        construirUri("/api/v1/inventario/elementos", null))
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                .header(CABECERA_IDENTIDAD, jugadorId)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(5))
                .build();
        HttpResponse<String> respuesta = enviar(peticion, "otorgar el botin");
        if (respuesta.statusCode() != 201) {
            throw new IntegracionBotinException(
                    "Inventario respondio " + respuesta.statusCode() + " al otorgar el botin");
        }
    }

    private Set<String> consultarEquipados(String propietarioId, String heroeId) {
        HttpRequest peticion = HttpRequest.newBuilder(construirUri(
                        "/api/v1/inventario/heroes/" + heroeId + "/equipamiento",
                        null))
                .GET()
                .header(CABECERA_IDENTIDAD, propietarioId)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(5))
                .build();
        HttpResponse<String> respuesta = enviar(peticion, "consultar el equipamiento enemigo");
        if (respuesta.statusCode() != 200) {
            throw new IntegracionBotinException(
                    "Inventario respondio " + respuesta.statusCode() + " al consultar equipamiento");
        }
        try {
            EquipamientoJson equipamiento = objectMapper.readValue(
                    respuesta.body(),
                    EquipamientoJson.class);
            Set<String> equipados = new HashSet<>();
            if (equipamiento.armas() != null) {
                equipados.addAll(equipamiento.armas());
            }
            if (equipamiento.armaduras() != null) {
                equipados.addAll(equipamiento.armaduras().values());
            }
            if (equipamiento.items() != null) {
                equipados.addAll(equipamiento.items());
            }
            return equipados;
        } catch (IOException excepcion) {
            throw new IntegracionBotinException(
                    "La respuesta de equipamiento no es valida",
                    excepcion);
        }
    }

    private PaginaInventarioJson consultarPagina(String propietarioId, int pagina) {
        HttpRequest peticion = HttpRequest.newBuilder(construirUri(
                        "/api/v1/inventario/elementos",
                        "pagina=" + pagina))
                .GET()
                .header(CABECERA_IDENTIDAD, propietarioId)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(5))
                .build();
        HttpResponse<String> respuesta = enviar(peticion, "consultar el inventario enemigo");
        if (respuesta.statusCode() != 200) {
            throw new IntegracionBotinException(
                    "Inventario respondio " + respuesta.statusCode() + " al consultar elementos");
        }
        try {
            return objectMapper.readValue(respuesta.body(), PaginaInventarioJson.class);
        } catch (IOException excepcion) {
            throw new IntegracionBotinException("La respuesta de inventario no es valida", excepcion);
        }
    }

    private java.util.Optional<ElementoCandidatoBotin> convertir(
            ElementoJson elemento,
            Set<String> equipados) {
        TipoBotin tipo;
        try {
            tipo = TipoBotin.valueOf(elemento.tipo());
        } catch (IllegalArgumentException | NullPointerException excepcion) {
            return java.util.Optional.empty();
        }
        ParteArmaduraBotin parte = null;
        if (tipo == TipoBotin.ARMADURA) {
            try {
                parte = ParteArmaduraBotin.valueOf(elemento.parteArmadura());
            } catch (IllegalArgumentException | NullPointerException excepcion) {
                throw new IntegracionBotinException("Inventario devolvio una armadura sin parte valida", excepcion);
            }
        }
        OrigenBotin origen = equipados.contains(elemento.id())
                ? OrigenBotin.EQUIPADO
                : OrigenBotin.ALMACENADO;
        return java.util.Optional.of(new ElementoCandidatoBotin(
                elemento.id(),
                elemento.productoId(),
                tipo,
                elemento.nombrePropio(),
                parte,
                origen));
    }

    private HttpResponse<String> enviar(HttpRequest peticion, String operacion) {
        try {
            return httpClient.send(peticion, HttpResponse.BodyHandlers.ofString());
        } catch (IOException excepcion) {
            throw new IntegracionBotinException("No se pudo " + operacion, excepcion);
        } catch (InterruptedException excepcion) {
            Thread.currentThread().interrupt();
            throw new IntegracionBotinException("Se interrumpio la operacion para " + operacion, excepcion);
        }
    }

    private URI construirUri(String ruta, String consulta) {
        try {
            return new URI(baseUri.getScheme(), baseUri.getAuthority(), ruta, consulta, null);
        } catch (URISyntaxException excepcion) {
            throw new IntegracionBotinException("No se pudo construir la URL de inventario", excepcion);
        }
    }

    private static void exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " es obligatorio");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PaginaInventarioJson(List<ElementoJson> elementos, boolean ultima) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ElementoJson(
            String id,
            String productoId,
            String tipo,
            String nombrePropio,
            String parteArmadura) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EquipamientoJson(
            List<String> armas,
            Map<String, String> armaduras,
            List<String> items) {
    }

    private record SolicitudCrearElemento(
            String productoId,
            TipoBotin tipo,
            String nombrePropio,
            ParteArmaduraBotin parteArmadura) {
    }
}
