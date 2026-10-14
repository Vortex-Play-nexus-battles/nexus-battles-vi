package nexus.inventario.aplicacion;

import java.util.Objects;

/**
 * El héroe que lleva puesto un objeto (inventario.yaml 1.7.0, {@code equipadoEn}).
 *
 * <p>Lo calcula el servidor con el agregado que ya tiene cargado: así la vitrina
 * de objetos dice «Equipado en …» y el selector de equipamiento descarta lo que
 * lleva otro héroe sin tener que pedir el equipo de todos los héroes del jugador.
 * Solo viaja al dueño del inventario: es el nombre que él mismo le puso a su héroe.
 */
public record EquipadoEn(String heroeId, String nombreHeroe) {

    public EquipadoEn {
        Objects.requireNonNull(heroeId, "heroeId es obligatorio");
    }
}
