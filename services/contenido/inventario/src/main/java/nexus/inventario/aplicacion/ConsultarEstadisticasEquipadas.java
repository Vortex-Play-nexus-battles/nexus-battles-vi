package nexus.inventario.aplicacion;

import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.ElementoNoEncontradoException;
import nexus.inventario.dominio.EstadisticasHeroe;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.RepositorioDeInventarios;
import org.springframework.stereotype.Service;

/**
 * HU-INV-006: expone {@link CalcularEstadisticasEquipadas} como caso de
 * uso, resolviendo el {@link Inventario} del heroeId con la misma regla de
 * identidad y propiedad que {@link GestionarEquipamiento#consultar}: la
 * identidad llega en la cabecera y nunca en la ruta, y el heroe debe
 * pertenecer a quien consulta. No persiste nada nuevo, solo calcula.
 */
@Service
public class ConsultarEstadisticasEquipadas {

    private final RepositorioDeInventarios repositorio;
    private final CalcularEstadisticasEquipadas calculo;

    public ConsultarEstadisticasEquipadas(RepositorioDeInventarios repositorio, CalcularEstadisticasEquipadas calculo) {
        this.repositorio = repositorio;
        this.calculo = calculo;
    }

    /** Las estadisticas del heroe con su equipo, y el nivel en que se calcularon (B4). */
    public record EstadisticasEnSuNivel(int nivel, EstadisticasHeroe estadisticas) {
    }

    public EstadisticasEnSuNivel consultar(String identidad, String heroeId) {
        Inventario inventario = inventarioPropio(identidad, heroeId);
        Integer nivel = inventario.elemento(heroeId).nivel();
        return new EstadisticasEnSuNivel(
                nivel == null ? ElementoInventario.NIVEL_INICIAL : nivel,
                calculo.calcular(inventario, heroeId));
    }

    /** Misma regla de propiedad que {@link GestionarEquipamiento#inventarioPropio}. */
    private Inventario inventarioPropio(String identidad, String heroeId) {
        String propietarioId = exigirIdentidad(identidad);
        Inventario inventario = repositorio.buscarPorElementoId(heroeId)
                .orElseThrow(ElementoNoEncontradoException::new);
        if (!inventario.propietarioId().equalsIgnoreCase(propietarioId)) {
            throw new InventarioAjenoException();
        }
        return inventario;
    }

    private String exigirIdentidad(String identidad) {
        if (identidad == null || identidad.isBlank()) {
            throw new IdentidadRequeridaException();
        }
        return identidad.trim();
    }
}
