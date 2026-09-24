package nexus.semilla;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Forma de {@code semilla/catalogo-inicial.json}: los productos extraidos
 * literalmente de las reglas del curso, agrupados por tipo, y los precios de
 * demostracion que decidio el PO (el curso no fija precios).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogoInicial(
        List<EntradaCatalogo> heroes,
        List<EntradaCatalogo> armas,
        List<EntradaCatalogo> armaduras,
        List<EntradaCatalogo> items,
        List<EntradaCatalogo> epicas,
        PreciosDemostracion preciosDemostracion) {

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
