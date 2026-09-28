package nexus.combate.reglas;

import java.util.List;
import java.util.Objects;

/**
 * Una accion por resolver.
 *
 * @param accion       codigo de la accion, nombre de la Tabla 7 o de la Tabla 20
 * @param ejecutor     {@code id} de quien actua
 * @param objetivo     {@code id} del objetivo, o nulo
 * @param porEquipos   combate cooperativo por equipos
 * @param combatientes todos los de la partida
 */
public record SolicitudDeAccion(String accion, String ejecutor, String objetivo, boolean porEquipos,
                                List<Contendiente> combatientes) {

    public SolicitudDeAccion {
        if (accion == null || accion.isBlank()) {
            throw new IllegalArgumentException("Hace falta la accion.");
        }
        if (ejecutor == null || ejecutor.isBlank()) {
            throw new IllegalArgumentException("Hace falta quien actua.");
        }
        combatientes = List.copyOf(Objects.requireNonNull(combatientes, "Hacen falta los combatientes."));
    }
}
