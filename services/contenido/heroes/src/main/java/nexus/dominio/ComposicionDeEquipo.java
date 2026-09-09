package nexus.dominio;

import java.util.List;

/**
 * HU-JUE-009: composicion de un equipo de combate. Regla del cliente dictada
 * en clase el 2026-07-29 (RG-054 / RC-08): "solo puede haber un healer por
 * grupo. No pueden ser todos healers. Un chaman o un medico, un sanador".
 * El ERS la recoge en CU-44 (paso 5, excepcion E4).
 *
 * El enfrentamiento individual (equipo de un solo heroe) se rige por un
 * parametro propio, independiente de la regla del sanador unico (criterio 3
 * de la historia): el cliente sostuvo tres posiciones sucesivas (V-09 /
 * PD-002) y la ultima registrada es RC-09, "los sanadores solo participan en
 * combate por equipos". El valor del parametro lo fija la configuracion del
 * servicio, no esta clase.
 */
public final class ComposicionDeEquipo {

    /** "Un chaman o un medico, un sanador" (RC-08). */
    public static final int SANADORES_MAXIMOS_POR_EQUIPO = 1;

    public static final String MOTIVO_SANADOR_UNICO = "Un equipo admite un único sanador: Chamán o Médico.";
    public static final String MOTIVO_SANADOR_EN_INDIVIDUAL = "Los sanadores solo participan en combate por equipos.";

    private ComposicionDeEquipo() {
    }

    /**
     * Veredicto sobre una composicion. motivo es null cuando es valida; cuando
     * no lo es, lleva el mensaje apto para el jugador (regla del cliente del
     * 2026-08-13: nunca vocabulario de protocolo).
     */
    public record Veredicto(boolean valida, String motivo, int sanadores, boolean individual) {
    }

    public static Veredicto validar(List<Prototipo> miembros, boolean sanadorEnIndividualPermitido) {
        if (miembros == null || miembros.isEmpty()) {
            throw new IllegalArgumentException("Un equipo tiene al menos un héroe.");
        }
        int sanadores = (int) miembros.stream().filter(Prototipo::esSanador).count();
        boolean individual = miembros.size() == 1;

        if (individual) {
            boolean rechazado = sanadores > 0 && !sanadorEnIndividualPermitido;
            return new Veredicto(!rechazado, rechazado ? MOTIVO_SANADOR_EN_INDIVIDUAL : null, sanadores, true);
        }
        boolean rechazado = sanadores > SANADORES_MAXIMOS_POR_EQUIPO;
        return new Veredicto(!rechazado, rechazado ? MOTIVO_SANADOR_UNICO : null, sanadores, false);
    }
}
