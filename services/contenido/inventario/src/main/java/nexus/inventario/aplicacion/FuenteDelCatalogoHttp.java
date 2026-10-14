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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import nexus.inventario.dominio.ParteArmadura;
import nexus.inventario.dominio.TipoElementoInventario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Lee el texto buscable del catálogo por REST (regla 7: nunca la base de otro
 * servicio):
 *
 * <ul>
 *   <li>{@code GET /api/v1/productos?page=N&size=50} (productos.yaml, lectura
 *       pública): nombre, tipo, parte, descripción, efectos y prototipo de cada
 *       producto, y sus atributos con la misma etiqueta que enseña la ficha
 *       («Poder de ataque 42», «Defensa 3», «Turnos de carga 2»…: §7.1, «cualquier
 *       información presente en el producto»; segunda auditoría, 13-oct). Las
 *       épicas y habilidades añaden el nombre del héroe al que van asociadas.</li>
 *   <li>{@code GET /api/v1/heroes} y {@code GET /api/v1/heroes/{nombre}}
 *       (heroes.yaml, públicas): la descripción del prototipo y sus tres acciones
 *       (Tabla 7) con su nombre, su costo y su efecto, que se cuelgan del producto
 *       HEROE con ese prototipo. Si el servicio de héroes no responde, el índice
 *       se construye sin ellas.</li>
 * </ul>
 *
 * <p>La lectura pública no enseña los productos suspendidos: esos se siguen
 * encontrando por el nombre propio, el tipo y la parte del elemento, que el
 * inventario sí guarda.
 */
@Component
public class FuenteDelCatalogoHttp implements FuenteDelCatalogo {

    private static final Logger BITACORA = LoggerFactory.getLogger(FuenteDelCatalogoHttp.class);
    private static final int TAMANIO_DE_PAGINA = 50;
    /** Tope de páginas: 1000 productos. El catálogo oficial tiene 56. */
    private static final int MAXIMO_DE_PAGINAS = 20;
    private static final Duration ESPERA = Duration.ofSeconds(3);
    private static final String SEPARADOR = " · ";

    private final URI productos;
    private final URI heroes;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    public FuenteDelCatalogoHttp(
            @Value("${productos.base-url}") String productosUrl,
            @Value("${heroes.base-url}") String heroesUrl) {
        this(URI.create(productosUrl), URI.create(heroesUrl),
                HttpClient.newBuilder().connectTimeout(ESPERA).build());
    }

    public FuenteDelCatalogoHttp(URI productos, URI heroes, HttpClient http) {
        this.productos = Objects.requireNonNull(productos, "productos es obligatorio");
        this.heroes = Objects.requireNonNull(heroes, "heroes es obligatorio");
        this.http = Objects.requireNonNull(http, "http es obligatorio");
    }

    @Override
    public Map<String, String> textosBuscables() {
        List<ProductoJson> catalogo = leerCatalogo();
        Map<String, String> nombrePorId = new HashMap<>();
        catalogo.forEach(producto -> nombrePorId.put(producto.id(), producto.nombre()));
        Map<String, String> fichaPorPrototipo = leerFichasDeHeroes();

        Map<String, String> textos = new LinkedHashMap<>();
        for (ProductoJson producto : catalogo) {
            if (producto.id() == null) {
                continue;
            }
            List<String> partes = new ArrayList<>();
            partes.add(producto.nombre());
            partes.add(nombreDelTipo(producto.tipo()));
            partes.add(producto.tipo());
            partes.add(nombreDeLaParte(producto.parte()));
            partes.add(producto.descripcion());
            partes.add(producto.efecto());
            partes.add(producto.efectoGeneral());
            partes.add(producto.efectoPotenciado());
            partes.add(producto.prototipo());
            partes.add(atributo("Costo de poder", producto.costoPoder()));
            partes.add(atributo("Multiplicador por nivel", producto.multiplicadorNivel()));
            partes.add(atributo("Turnos de carga", producto.turnosCarga()));
            partes.add(atributo("Poder de ataque", producto.poderDeAtaque()));
            partes.add(atributo("Defensa", producto.defensa()));
            partes.add(atributo("Turnos de recarga", producto.turnosRecarga()));
            if (producto.heroe() != null) {
                partes.add(nombrePorId.get(producto.heroe()));
            }
            if (producto.prototipo() != null) {
                partes.add(fichaPorPrototipo.get(producto.prototipo()));
            }
            textos.put(producto.id(), unir(partes.stream()));
        }
        return textos;
    }

    private List<ProductoJson> leerCatalogo() {
        List<ProductoJson> catalogo = new ArrayList<>();
        for (int pagina = 0; pagina < MAXIMO_DE_PAGINAS; pagina++) {
            URI uri = uri(productos, "/api/v1/productos", "page=" + pagina + "&size=" + TAMANIO_DE_PAGINA);
            PaginaJson leida = leer(uri, PaginaJson.class, "productos");
            if (leida.content() != null) {
                catalogo.addAll(leida.content());
            }
            int totalPaginas = leida.totalPages() == null ? 0 : leida.totalPages();
            if (leida.content() == null || leida.content().isEmpty() || pagina + 1 >= totalPaginas) {
                return catalogo;
            }
        }
        return catalogo;
    }

    /** prototipo → «tipo · descripción · acción efecto · …»; vacío si héroes no responde. */
    private Map<String, String> leerFichasDeHeroes() {
        Map<String, String> fichas = new HashMap<>();
        try {
            ResumenHeroeJson[] resumenes = leer(uri(heroes, "/api/v1/heroes", null),
                    ResumenHeroeJson[].class, "heroes");
            for (ResumenHeroeJson resumen : resumenes) {
                if (resumen.nombre() == null) {
                    continue;
                }
                FichaHeroeJson ficha = leer(uri(heroes, "/api/v1/heroes/" + resumen.nombre(), null),
                        FichaHeroeJson.class, "heroes");
                Stream<String> acciones = ficha.acciones() == null
                        ? Stream.empty()
                        : ficha.acciones().stream()
                                .flatMap(accion -> Stream.of(accion.nombre(), accion.costo(), accion.efecto()));
                fichas.put(resumen.nombre(), unir(Stream.concat(
                        Stream.of(ficha.tipo(), ficha.descripcion()), acciones)));
            }
        } catch (ResolutorDeProductoException fallo) {
            BITACORA.warn("Fichas de heroes no disponibles para el indice de busqueda ({})", fallo.getMessage());
        }
        return fichas;
    }

    private <T> T leer(URI uri, Class<T> tipo, String servicio) {
        HttpRequest peticion = HttpRequest.newBuilder(uri)
                .GET()
                .header("Accept", "application/json")
                .timeout(ESPERA)
                .build();
        try {
            HttpResponse<String> respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() != 200) {
                throw new ResolutorDeProductoException(
                        "El servicio de " + servicio + " respondio " + respuesta.statusCode() + " en " + uri.getPath());
            }
            return json.readValue(respuesta.body(), tipo);
        } catch (IOException fallo) {
            throw new ResolutorDeProductoException("No se pudo leer " + uri.getPath() + " de " + servicio, fallo);
        } catch (InterruptedException fallo) {
            Thread.currentThread().interrupt();
            throw new ResolutorDeProductoException("Lectura de " + servicio + " interrumpida", fallo);
        }
    }

    /** Constructor de cinco argumentos: codifica espacios y tildes de «Pícaro Veneno». */
    private static URI uri(URI base, String ruta, String consulta) {
        try {
            return new URI(base.getScheme(), base.getAuthority(), ruta, consulta, null);
        } catch (URISyntaxException fallo) {
            throw new ResolutorDeProductoException("Ruta invalida: " + ruta, fallo);
        }
    }

    private static String unir(Stream<String> partes) {
        return partes.filter(Objects::nonNull)
                .map(String::strip)
                .filter(parte -> !parte.isEmpty())
                .collect(Collectors.joining(SEPARADOR));
    }

    /** «Etiqueta valor», como en la ficha; sin valor, nada. 1.0 se escribe 1. */
    static String atributo(String etiqueta, Number valor) {
        if (valor == null) {
            return null;
        }
        double numero = valor.doubleValue();
        String texto = numero == Math.rint(numero) && !Double.isInfinite(numero)
                ? Long.toString((long) numero)
                : java.math.BigDecimal.valueOf(numero).stripTrailingZeros().toPlainString();
        return etiqueta + " " + texto;
    }

    private static String nombreDelTipo(String tipo) {
        if (tipo == null) {
            return null;
        }
        try {
            return TextoBuscable.NOMBRE_DEL_TIPO.get(TipoElementoInventario.valueOf(tipo));
        } catch (IllegalArgumentException tipoDesconocido) {
            return null;
        }
    }

    private static String nombreDeLaParte(String parte) {
        if (parte == null) {
            return null;
        }
        try {
            return TextoBuscable.NOMBRE_DE_LA_PARTE.get(ParteArmadura.valueOf(parte));
        } catch (IllegalArgumentException parteDesconocida) {
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PaginaJson(List<ProductoJson> content, Integer totalPages) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProductoJson(
            String id,
            String nombre,
            String tipo,
            String descripcion,
            String parte,
            String efecto,
            String efectoGeneral,
            String efectoPotenciado,
            String prototipo,
            String heroe,
            Number costoPoder,
            Number multiplicadorNivel,
            Number turnosCarga,
            Number poderDeAtaque,
            Number defensa,
            Number turnosRecarga) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ResumenHeroeJson(String nombre) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FichaHeroeJson(String tipo, String descripcion, List<AccionJson> acciones) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AccionJson(String nombre, String costo, String efecto) {
    }
}
