package nexus.inventario.aplicacion;

import nexus.inventario.dominio.ElementoNoEncontradoException;
import nexus.inventario.dominio.EquipamientoHeroe;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.RepositorioDeInventarios;
import org.springframework.stereotype.Service;

@Service
public class GestionarEquipamiento {

    private final RepositorioDeInventarios repositorio;

    public GestionarEquipamiento(RepositorioDeInventarios repositorio) {
        this.repositorio = repositorio;
    }

    public EquipamientoHeroe consultar(String identidad, String heroeId) {
        Inventario inventario = inventarioPropio(identidad, heroeId);
        return inventario.equipamiento(heroeId);
    }

    public EquipamientoHeroe equipar(String identidad, String heroeId, String elementoId) {
        Inventario inventario = inventarioPropio(identidad, heroeId);
        exigirElementoPropio(inventario, elementoId);
        Inventario guardado = repositorio.guardar(inventario.equipar(heroeId, elementoId));
        return guardado.equipamiento(heroeId);
    }

    public EquipamientoHeroe desequipar(String identidad, String heroeId, String elementoId) {
        Inventario inventario = inventarioPropio(identidad, heroeId);
        exigirElementoPropio(inventario, elementoId);
        Inventario guardado = repositorio.guardar(inventario.desequipar(heroeId, elementoId));
        return guardado.equipamiento(heroeId);
    }

    private Inventario inventarioPropio(String identidad, String heroeId) {
        String propietarioId = exigirIdentidad(identidad);
        Inventario inventario = repositorio.buscarPorElementoId(heroeId)
                .orElseThrow(ElementoNoEncontradoException::new);
        if (!inventario.propietarioId().equalsIgnoreCase(propietarioId)) {
            throw new InventarioAjenoException();
        }
        return inventario;
    }

    private void exigirElementoPropio(Inventario inventario, String elementoId) {
        Inventario inventarioDelElemento = repositorio.buscarPorElementoId(elementoId)
                .orElseThrow(ElementoNoEncontradoException::new);
        if (!inventarioDelElemento.propietarioId().equalsIgnoreCase(inventario.propietarioId())) {
            throw new InventarioAjenoException();
        }
    }

    private String exigirIdentidad(String identidad) {
        if (identidad == null || identidad.isBlank()) {
            throw new IdentidadRequeridaException();
        }
        return identidad.trim();
    }
}
