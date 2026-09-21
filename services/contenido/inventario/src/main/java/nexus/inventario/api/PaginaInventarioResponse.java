package nexus.inventario.api;

import java.util.List;
import nexus.inventario.aplicacion.PaginaInventario;

public record PaginaInventarioResponse(
        List<ElementoInventarioResponse> elementos,
        int numero,
        int tamanio,
        int totalElementos,
        int totalPaginas,
        boolean ultima) {

    static PaginaInventarioResponse de(PaginaInventario pagina) {
        return new PaginaInventarioResponse(
                pagina.elementos().stream().map(ElementoInventarioResponse::de).toList(),
                pagina.numero(), pagina.tamanio(), pagina.totalElementos(),
                pagina.totalPaginas(), pagina.ultima());
    }
}
