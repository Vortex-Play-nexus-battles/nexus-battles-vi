package nexus.inventario.aplicacion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.EquipamientoHeroe;
import nexus.inventario.dominio.Inventario;

/**
 * Resultado estable de una consulta paginada del inventario.
 *
 * <p>1.7.0: {@code equipados} dice qué héroe lleva cada elemento de la página
 * ({@link EquipadoEn}); los que no están puestos no aparecen.
 */
public record PaginaInventario(
        List<ElementoInventario> elementos,
        int numero,
        int tamanio,
        int totalElementos,
        int totalPaginas,
        boolean ultima,
        Map<String, EquipadoEn> equipados) {

    public PaginaInventario {
        elementos = List.copyOf(Objects.requireNonNull(
                elementos, "elementos no puede ser nulo"));
        if (numero < 0 || tamanio <= 0 || totalElementos < 0 || totalPaginas < 0) {
            throw new IllegalArgumentException("Los metadatos de paginacion no pueden ser negativos");
        }
        equipados = equipados == null ? Map.of() : Map.copyOf(equipados);
    }

    /** La forma anterior a 1.7.0, sin saber quién lleva cada elemento. */
    public PaginaInventario(
            List<ElementoInventario> elementos,
            int numero,
            int tamanio,
            int totalElementos,
            int totalPaginas,
            boolean ultima) {
        this(elementos, numero, tamanio, totalElementos, totalPaginas, ultima, Map.of());
    }

    /** El héroe que lleva puesto ese elemento, o {@code null}. */
    public EquipadoEn equipadoEn(String elementoId) {
        return equipados.get(elementoId);
    }

    /**
     * Corta una página de {@link ConsultarInventarioPaginado#TAMANIO_PAGINA} sobre una
     * lista ya filtrada. Una página posterior a la última llega vacía y marcada como
     * última: pedir de más no es un error.
     */
    static PaginaInventario de(
            List<ElementoInventario> lista, int numeroPagina, Map<String, EquipadoEn> equipados) {
        int tamanio = ConsultarInventarioPaginado.TAMANIO_PAGINA;
        int totalElementos = lista.size();
        int totalPaginas = totalElementos == 0 ? 0 : (totalElementos + tamanio - 1) / tamanio;
        long inicioCalculado = (long) numeroPagina * tamanio;
        int inicio = (int) Math.min(inicioCalculado, totalElementos);
        int fin = Math.min(inicio + tamanio, totalElementos);
        boolean ultima = totalPaginas == 0 || numeroPagina >= totalPaginas - 1;
        List<ElementoInventario> enPagina = lista.subList(inicio, fin);
        Map<String, EquipadoEn> deLaPagina = new HashMap<>();
        for (ElementoInventario elemento : enPagina) {
            EquipadoEn portador = equipados.get(elemento.id());
            if (portador != null) {
                deLaPagina.put(elemento.id(), portador);
            }
        }
        return new PaginaInventario(
                enPagina, numeroPagina, tamanio, totalElementos, totalPaginas, ultima, deLaPagina);
    }

    /** Quién lleva cada elemento, según los equipamientos del agregado. */
    static Map<String, EquipadoEn> equipadosDe(Inventario inventario) {
        Map<String, String> nombres = new HashMap<>();
        for (ElementoInventario elemento : inventario.elementos()) {
            nombres.put(elemento.id(), elemento.nombrePropio());
        }
        Map<String, EquipadoEn> equipados = new HashMap<>();
        for (EquipamientoHeroe equipamiento : inventario.equipamientos()) {
            EquipadoEn portador = new EquipadoEn(
                    equipamiento.heroeId(), nombres.get(equipamiento.heroeId()));
            equipamiento.armas().forEach(id -> equipados.put(id, portador));
            equipamiento.items().forEach(id -> equipados.put(id, portador));
            equipamiento.armaduras().values().forEach(id -> equipados.put(id, portador));
        }
        return equipados;
    }
}
