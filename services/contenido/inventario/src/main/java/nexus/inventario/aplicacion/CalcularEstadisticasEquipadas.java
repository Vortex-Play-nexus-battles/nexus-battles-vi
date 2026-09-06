package nexus.inventario.aplicacion;

import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.EquipamientoHeroe;
import nexus.inventario.dominio.EstadisticasHeroe;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.ModificadorEstadisticas;
import org.springframework.stereotype.Service;

/**
 * HU-INV-006, criterios 1 y 2: calcula las estadisticas de un heroe con los
 * modificadores de su equipamiento actual aplicados, y las recalcula (sin
 * el modificador correspondiente) cuando un objeto deja de estar equipado.
 *
 * <p>La cadena de resolucion, confirmada por Nicolay (dueño de HU-INV-005):
 * <pre>
 * heroeId (id de ElementoInventario, tipo HEROE)
 *   -> Inventario.elemento(heroeId).productoId()             -> productoId del heroe
 *   -> ResolutorDeProducto.resolver(productoId del heroe)    -> prototipo
 *   -> ResolutorDeEstadisticasHeroe.resolver(prototipo)      -> estadisticas base
 *
 * cada elementoId equipado (arma/armadura/item)
 *   -> Inventario.elemento(elementoId).productoId()          -> productoId del elemento
 *   -> ResolutorDeProducto.resolver(productoId del elemento) -> nombre
 *   -> CatalogoEfectosEquipamiento.efectoDe(nombre)           -> modificador
 * </pre>
 * No se aplica ningun modificador de items cuyo efecto sea condicionado a
 * turnos de combate o afecte al oponente: ver CatalogoEfectosEquipamiento.
 */
@Service
public class CalcularEstadisticasEquipadas {

    private final ResolutorDeProducto productos;
    private final ResolutorDeEstadisticasHeroe heroes;

    public CalcularEstadisticasEquipadas(
            ResolutorDeProducto productos,
            ResolutorDeEstadisticasHeroe heroes) {
        this.productos = productos;
        this.heroes = heroes;
    }

    /**
     * @param inventario el inventario del jugador, para resolver productoId
     *                    a partir de cada id de elemento (heroe y equipados)
     * @param heroeId     id del ElementoInventario de tipo HEROE
     */
    public EstadisticasHeroe calcular(Inventario inventario, String heroeId) {
        String productoIdDelHeroe = productoIdDe(inventario, heroeId);
        ResolutorDeProducto.DetalleProducto heroeProducto = productos.resolver(productoIdDelHeroe);

        EstadisticasHeroe base = heroes.resolver(heroeProducto.prototipo());

        EquipamientoHeroe equipamiento = inventario.equipamiento(heroeId);
        ModificadorEstadisticas total = ModificadorEstadisticas.NULO;
        for (String elementoId : todosLosElementosEquipados(equipamiento)) {
            String productoIdDelElemento = productoIdDe(inventario, elementoId);
            ResolutorDeProducto.DetalleProducto detalle = productos.resolver(productoIdDelElemento);
            total = total.combinar(CatalogoEfectosEquipamiento.efectoDe(detalle.nombre()));
        }

        return base.aplicar(total);
    }

    private String productoIdDe(Inventario inventario, String elementoId) {
        ElementoInventario elemento = inventario.elemento(elementoId);
        return elemento.productoId();
    }

    private java.util.List<String> todosLosElementosEquipados(EquipamientoHeroe equipamiento) {
        java.util.List<String> todos = new java.util.ArrayList<>();
        todos.addAll(equipamiento.armas());
        todos.addAll(equipamiento.armaduras().values());
        todos.addAll(equipamiento.items());
        return todos;
    }
}
