package nexus.misiones.dominio.simulacion;

import java.util.List;

/**
 * Una accion resuelta por el motor. {@code accionEjecutada} difiere de
 * {@code accion} cuando faltaba poder: el turno se juega como ataque basico en
 * valor base y {@code enValorBase} es verdadero (seccion 6.1.1).
 *
 * @param ataque nulo si la accion no golpea (una defensa, una sanacion)
 */
public record ResultadoDeAccion(String accion, String accionEjecutada, boolean enValorBase, String ejecutor,
                                String objetivo, DetalleDeAtaque ataque, List<Suceso> sucesos,
                                List<Combatiente> combatientes) {

    public ResultadoDeAccion {
        sucesos = sucesos == null ? List.of() : List.copyOf(sucesos);
        combatientes = List.copyOf(combatientes);
    }
}
