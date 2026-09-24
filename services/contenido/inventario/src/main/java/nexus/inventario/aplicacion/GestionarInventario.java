package nexus.inventario.aplicacion;

import java.util.UUID;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.ElementoNoEncontradoException;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.ParteArmadura;
import nexus.inventario.dominio.RepositorioDeInventarios;
import nexus.inventario.dominio.TipoElementoInventario;
import org.springframework.stereotype.Service;

@Service
public class GestionarInventario {

    private static final String ESTADO_SUSPENDIDO = "SUSPENDIDO";

    private final RepositorioDeInventarios repositorio;
    private final ResolutorDeProducto productos;

    public GestionarInventario(RepositorioDeInventarios repositorio, ResolutorDeProducto productos) {
        this.repositorio = repositorio;
        this.productos = productos;
    }

    public ElementoInventario crear(
            String identidad,
            String productoId,
            TipoElementoInventario tipo,
            String nombrePropio) {
        return crear(identidad, productoId, tipo, nombrePropio, null);
    }

    public ElementoInventario crear(
            String identidad,
            String productoId,
            TipoElementoInventario tipo,
            String nombrePropio,
            ParteArmadura parteArmadura) {
        String propietarioId = exigirIdentidad(identidad);
        if (tipo == TipoElementoInventario.ARMADURA && parteArmadura == null) {
            throw new IllegalArgumentException("La armadura debe declarar su parte");
        }
        exigirProductoDelCatalogo(productoId, tipo);
        Inventario inventario = repositorio.buscarPorPropietario(propietarioId)
                .orElseGet(() -> Inventario.vacio(propietarioId));
        ElementoInventario nuevo = new ElementoInventario(
                UUID.randomUUID().toString(), productoId, tipo, nombrePropio, parteArmadura);
        Inventario guardado = repositorio.guardar(inventario.agregar(nuevo));
        return guardado.elemento(nuevo.id());
    }

    public ElementoInventario modificarNombre(
            String identidad,
            String elementoId,
            String nuevoNombre) {
        String propietarioId = exigirIdentidad(identidad);
        Inventario inventario = repositorio.buscarPorElementoId(elementoId)
                .orElseThrow(ElementoNoEncontradoException::new);
        if (!inventario.propietarioId().equalsIgnoreCase(propietarioId)) {
            throw new InventarioAjenoException();
        }
        Inventario guardado = repositorio.guardar(inventario.renombrarElemento(elementoId, nuevoNombre));
        return guardado.elemento(elementoId);
    }

    public void eliminar(String identidad, String elementoId) {
        String propietarioId = exigirIdentidad(identidad);
        Inventario inventario = repositorio.buscarPorElementoId(elementoId)
                .orElseThrow(ElementoNoEncontradoException::new);
        if (!inventario.propietarioId().equalsIgnoreCase(propietarioId)) {
            throw new InventarioAjenoException();
        }
        repositorio.guardar(inventario.eliminarElemento(elementoId));
    }

    /**
     * Un jugador solo tiene instancias de productos que existen en el
     * catalogo: los productos los crea el rol disenador (RG-074, 29-jul) y el
     * tipo lo manda el producto, no el formulario. Si productos no responde se
     * rechaza: aceptar a ciegas es justo lo que dejo entrar "espada-corta".
     */
    private void exigirProductoDelCatalogo(String productoId, TipoElementoInventario tipo) {
        ResolutorDeProducto.DetalleProducto producto;
        try {
            producto = productos.resolver(productoId);
        } catch (ProductoNoEncontradoException e) {
            throw new ProductoInexistenteException();
        } catch (ResolutorDeProductoException e) {
            throw new CatalogoNoDisponibleException(e);
        }
        // Una respuesta 200 sin tipo no describe un producto (p. ej. otra ruta
        // del servicio de productos alcanzada con un id raro).
        if (producto == null || producto.tipo() == null) {
            throw new ProductoInexistenteException();
        }
        if (ESTADO_SUSPENDIDO.equals(producto.estado())) {
            throw new ProductoSuspendidoException();
        }
        if (!tipo.name().equals(producto.tipo())) {
            throw new TipoNoCoincideException();
        }
    }

    private String exigirIdentidad(String identidad) {
        if (identidad == null || identidad.isBlank()) {
            throw new IdentidadRequeridaException();
        }
        return identidad.trim();
    }
}
