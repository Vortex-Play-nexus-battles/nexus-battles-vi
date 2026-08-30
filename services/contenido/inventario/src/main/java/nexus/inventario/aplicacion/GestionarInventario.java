package nexus.inventario.aplicacion;

import java.util.UUID;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.ElementoNoEncontradoException;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.RepositorioDeInventarios;
import nexus.inventario.dominio.TipoElementoInventario;
import org.springframework.stereotype.Service;

@Service
public class GestionarInventario {

    private final RepositorioDeInventarios repositorio;

    public GestionarInventario(RepositorioDeInventarios repositorio) {
        this.repositorio = repositorio;
    }

    public ElementoInventario crear(
            String identidad,
            String productoId,
            TipoElementoInventario tipo,
            String nombrePropio) {
        String propietarioId = exigirIdentidad(identidad);
        Inventario inventario = repositorio.buscarPorPropietario(propietarioId)
                .orElseGet(() -> Inventario.vacio(propietarioId));
        ElementoInventario nuevo = new ElementoInventario(
                UUID.randomUUID().toString(), productoId, tipo, nombrePropio);
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

    private String exigirIdentidad(String identidad) {
        if (identidad == null || identidad.isBlank()) {
            throw new IdentidadRequeridaException();
        }
        return identidad.trim();
    }
}
