package nexus.semilla;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Forma de {@code semilla/catalogo-inicial.json}: los productos extraidos
 * literalmente de las reglas del curso, agrupados por tipo, y los precios de
 * demostracion que decidio el PO (el curso no fija precios).
 *
 * <p>{@code version} (B4) es la version del CONTENIDO de la semilla. Quien
 * cambie un producto del archivo sube la version; al arrancar, la semilla pone
 * al dia los productos sembrados con una version menor que nadie edito. Un
 * archivo sin version se lee como la version 1.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogoInicial(
        Integer version,
        List<EntradaCatalogo> heroes,
        List<EntradaCatalogo> armas,
        List<EntradaCatalogo> armaduras,
        List<EntradaCatalogo> items,
        List<EntradaCatalogo> epicas,
        PreciosDemostracion preciosDemostracion) {

    /** La version de un archivo que no la declara. */
    public static final int VERSION_SIN_DECLARAR = 1;

    @JsonCreator
    public CatalogoInicial {
    }

    /** Sin version declarada (pruebas escritas antes de B4). */
    public CatalogoInicial(
            List<EntradaCatalogo> heroes,
            List<EntradaCatalogo> armas,
            List<EntradaCatalogo> armaduras,
            List<EntradaCatalogo> items,
            List<EntradaCatalogo> epicas,
            PreciosDemostracion preciosDemostracion) {
        this(null, heroes, armas, armaduras, items, epicas, preciosDemostracion);
    }

    /** La version del contenido; 1 si el archivo no la declara. */
    public int versionDelContenido() {
        return version == null ? VERSION_SIN_DECLARAR : version;
    }

    /** Todas las entradas, en el orden del archivo: primero los heroes. */
    public List<EntradaCatalogo> todas() {
        return Stream.of(heroes, armas, armaduras, items, epicas)
                .filter(lista -> lista != null)
                .flatMap(List::stream)
                .toList();
    }

    /** Una entrada del catalogo tal como viene en el JSON. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EntradaCatalogo(
            String id,
            String tipo,
            String nombre,
            String prototipo,
            String heroe,
            String grupo,
            String parte,
            String efectos,
            String probabilidadCaida,
            String efectoGeneral,
            String efectoPotenciado,
            String probabilidadMaster,
            Map<String, String> estadisticasNivel1,
            String fuente) {
    }

    /** Precios en creditos y en pesos (COP) por tipo, tiraje y modalidad de compra. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PreciosDemostracion(
            Map<String, Integer> creditos,
            Map<String, BigDecimal> cop,
            int tiraje,
            boolean premium) {
    }
}
