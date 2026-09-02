package nexus.inventario.aplicacion;

import java.util.List;
import java.util.Optional;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.EstadisticasHeroe;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.RepositorioDeInventarios;
import nexus.inventario.dominio.TipoElementoInventario;
import org.springframework.stereotype.Service;

/**
 * HU-SAL-003 (Simon, Grupo 6): resuelve el heroe activo del jugador
 * autenticado, con sus estadisticas ya combinadas con el equipamiento
 * actual — reutiliza {@link CalcularEstadisticasEquipadas} (HU-INV-006) tal
 * cual, sin modificarla.
 *
 * <p>No existe todavia ningun concepto de "heroe activo" en el dominio: se
 * infiere aqui a partir de cuantos {@link ElementoInventario} de tipo HEROE
 * tiene el jugador. Cero heroes no es un error (el jugador simplemente no
 * tiene ninguno todavia); dos o mas heroes tampoco es un error del jugador,
 * pero tampoco hay forma de saber cual mostrar sin un mecanismo de
 * seleccion explicita que no existe aun — ver
 * {@link SeleccionDeHeroeActivoNoDefinidaException}.
 */
@Service
public class ConsultarHeroeActivo {

    private final RepositorioDeInventarios repositorio;
    private final CalcularEstadisticasEquipadas calculo;

    public ConsultarHeroeActivo(RepositorioDeInventarios repositorio, CalcularEstadisticasEquipadas calculo) {
        this.repositorio = repositorio;
        this.calculo = calculo;
    }

    /**
     * @return vacio si el jugador no tiene ningun heroe en su inventario
     * @throws IdentidadRequeridaException si no hay identidad
     * @throws SeleccionDeHeroeActivoNoDefinidaException si el jugador tiene
     *         mas de un heroe
     */
    public Optional<HeroeActivo> consultar(String identidad) {
        String propietarioId = exigirIdentidad(identidad);
        Inventario inventario = repositorio.buscarPorPropietario(propietarioId)
                .orElseGet(() -> Inventario.vacio(propietarioId));

        List<ElementoInventario> heroes = inventario.elementos().stream()
                .filter(elemento -> elemento.tipo() == TipoElementoInventario.HEROE)
                .toList();

        if (heroes.isEmpty()) {
            return Optional.empty();
        }
        if (heroes.size() > 1) {
            throw new SeleccionDeHeroeActivoNoDefinidaException();
        }

        ElementoInventario heroe = heroes.get(0);
        EstadisticasHeroe estadisticas = calculo.calcular(inventario, heroe.id());
        return Optional.of(new HeroeActivo(heroe.nombrePropio(), estadisticas));
    }

    private String exigirIdentidad(String identidad) {
        if (identidad == null || identidad.isBlank()) {
            throw new IdentidadRequeridaException();
        }
        return identidad.trim();
    }

    public record HeroeActivo(String nombre, EstadisticasHeroe estadisticas) {
    }
}
