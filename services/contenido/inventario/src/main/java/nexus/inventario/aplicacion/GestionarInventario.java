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

    /**
     * Alta de un elemento por {@code POST /elementos}. Desde B4 solo la usan un
     * servicio o un administrador (la cadena de seguridad lo exige); un jugador
     * recibe su propiedad por {@code POST /entregas}.
     *
     * @param parteArmadura la que manda quien crea; desde B4 la parte la decide
     *                      el catalogo: si llega y no coincide con la del
     *                      producto, se rechaza, y si no llega se toma del
     *                      catalogo
     */
    public ElementoInventario crear(
            String identidad,
            String productoId,
            TipoElementoInventario tipo,
            String nombrePropio,
            ParteArmadura parteArmadura) {
        String propietarioId = exigirIdentidad(identidad);
        ResolutorDeProducto.DetalleProducto producto = exigirProductoDelCatalogo(productoId, tipo);
        ParteArmadura parte = parteSegunElCatalogo(tipo, parteArmadura, producto);
        Inventario inventario = repositorio.buscarPorPropietario(propietarioId)
                .orElseGet(() -> Inventario.vacio(propietarioId));
        ElementoInventario nuevo = new ElementoInventario(
                UUID.randomUUID().toString(), productoId, tipo, nombrePropio, parte);
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
    private ResolutorDeProducto.DetalleProducto exigirProductoDelCatalogo(
            String productoId, TipoElementoInventario tipo) {
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
        return producto;
    }

    /**
     * B4: la parte de una armadura es del producto. La que manda quien crea se
     * acepta solo si coincide; si el catalogo no la declara (productos
     * anteriores a que la exigiera el alta), vale la que llega, y una armadura
     * sin parte en ningun lado no se puede crear.
     */
    private static ParteArmadura parteSegunElCatalogo(
            TipoElementoInventario tipo, ParteArmadura pedida, ResolutorDeProducto.DetalleProducto producto) {
        if (tipo != TipoElementoInventario.ARMADURA) {
            return pedida;
        }
        ParteArmadura delCatalogo = producto.parteArmadura();
        if (pedida != null && delCatalogo != null && pedida != delCatalogo) {
            throw new ParteNoCoincideException();
        }
        ParteArmadura parte = delCatalogo != null ? delCatalogo : pedida;
        if (parte == null) {
            throw new IllegalArgumentException("La armadura debe declarar su parte");
        }
        return parte;
    }

    private String exigirIdentidad(String identidad) {
        if (identidad == null || identidad.isBlank()) {
            throw new IdentidadRequeridaException();
        }
        return identidad.trim();
    }
}
