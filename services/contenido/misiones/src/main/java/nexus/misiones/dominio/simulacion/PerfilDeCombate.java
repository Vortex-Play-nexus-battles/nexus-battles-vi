package nexus.misiones.dominio.simulacion;

import java.util.List;

/**
 * Lo que el heroe lleva al combate ademas de su nivel: sus estadisticas con el
 * equipamiento aplicado, los nombres de lo que lleva puesto (el motor aplica
 * sus efectos de combate, Tablas 8 a 19) y las epicas que tiene (Tabla 20).
 *
 * @param estadisticas nulas = el motor usa las del catalogo en su nivel, sin equipo
 */
public record PerfilDeCombate(EstadisticasDeCombate estadisticas, List<String> equipamiento, List<String> epicas) {

    public PerfilDeCombate {
        equipamiento = equipamiento == null ? List.of() : List.copyOf(equipamiento);
        epicas = epicas == null ? List.of() : List.copyOf(epicas);
    }

    /** Un heroe del que no se conoce mas que su nivel: pelea con lo del catalogo. */
    public static PerfilDeCombate delCatalogo() {
        return new PerfilDeCombate(null, List.of(), List.of());
    }
}
