package nexus.inventario.aplicacion;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.RepositorioDeInventarios;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Busca en el inventario propio (HU-INV-002, §7.1).
 *
 * <p>§7.1: «búsqueda para cualquier información presente en el producto» con
 * «algoritmos de indexación que permitan identificar en pocos caracteres (mínimo
 * cuatro)». Un elemento coincide si el criterio aparece:
 *
 * <ul>
 *   <li>en lo que guarda el inventario: su nombre propio, su tipo, su parte de
 *       armadura o el identificador de su producto; o</li>
 *   <li>en la información de su producto en el catálogo —nombre, descripción,
 *       efectos, prototipo y, en los héroes, sus acciones—, resuelta con el
 *       índice de cuatrigramas del catálogo ({@link IndiceDeBusquedaDelCatalogo}).</li>
 * </ul>
 *
 * <p>Siempre como subcadena, sin tildes ni mayúsculas: «brum» encuentra «Amuleto
 * de Bruma». Solo se lee el inventario de quien busca (índice único por dueño):
 * nada de otro jugador puede aparecer. Hasta 1.6.0 se prefiltraba con el índice
 * {@code $text} de MongoDB, que solo reconoce palabras completas.
 */
@Service
public class BuscarElementosInventario {

    private static final int MINIMO_CARACTERES = 4;
    private final RepositorioDeInventarios repositorio;
    private final BuscadorEnElCatalogo catalogo;

    /** Sin catálogo: solo los datos propios de cada elemento. */
    public BuscarElementosInventario(RepositorioDeInventarios repositorio) {
        this(repositorio, BuscadorEnElCatalogo.NINGUNO);
    }

    @Autowired
    public BuscarElementosInventario(RepositorioDeInventarios repositorio, BuscadorEnElCatalogo catalogo) {
        this.repositorio = Objects.requireNonNull(repositorio, "repositorio es obligatorio");
        this.catalogo = Objects.requireNonNull(catalogo, "catalogo es obligatorio");
    }

    public PaginaInventario buscar(String identidad, String criterio, int numeroPagina) {
        String propietarioId = exigirIdentidad(identidad);
        String texto = exigirCriterio(criterio);
        if (numeroPagina < 0) {
            throw new IllegalArgumentException("numeroPagina no puede ser negativo");
        }

        Optional<Inventario> inventario = repositorio.buscarPorPropietario(propietarioId);
        if (inventario.isEmpty() || inventario.get().elementos().isEmpty()) {
            return PaginaInventario.de(List.of(), numeroPagina, Map.of());
        }
        String buscado = TextoBuscable.normalizar(texto);
        Set<String> productos = catalogo.productosQueCoinciden(texto);
        List<ElementoInventario> coincidencias = inventario.get().elementos().stream()
                .filter(elemento -> productos.contains(elemento.productoId())
                        || datosPropios(elemento).contains(buscado))
                .toList();
        return PaginaInventario.de(coincidencias, numeroPagina, PaginaInventario.equipadosDe(inventario.get()));
    }

    /** Lo que el inventario guarda del elemento, normalizado para comparar. */
    private static String datosPropios(ElementoInventario elemento) {
        return TextoBuscable.normalizar(String.join(" · ", Stream.of(
                        elemento.nombrePropio(),
                        elemento.productoId(),
                        elemento.tipo() == null ? null : elemento.tipo().name(),
                        elemento.tipo() == null ? null : TextoBuscable.NOMBRE_DEL_TIPO.get(elemento.tipo()),
                        elemento.parteArmadura() == null ? null : elemento.parteArmadura().name(),
                        elemento.parteArmadura() == null
                                ? null
                                : TextoBuscable.NOMBRE_DE_LA_PARTE.get(elemento.parteArmadura()))
                .filter(Objects::nonNull)
                .toList()));
    }

    private String exigirIdentidad(String identidad) {
        if (identidad == null || identidad.isBlank()) {
            throw new IdentidadRequeridaException();
        }
        return identidad.trim();
    }

    private String exigirCriterio(String criterio) {
        if (criterio == null || criterio.trim().length() < MINIMO_CARACTERES) {
            throw new CriterioBusquedaInvalidoException();
        }
        return criterio.trim();
    }
}
