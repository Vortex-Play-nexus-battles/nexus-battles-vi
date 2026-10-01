package nexus.misiones.dominio.simulacion;

import java.util.List;
import java.util.Objects;
import nexus.misiones.dominio.Epica;

/**
 * Un enemigo ya preparado para el combate: sus estadisticas resueltas (de la
 * semilla o de la vista por nivel de heroes) y escaladas por el escalon.
 *
 * @param rotaciones su estrategia predefinida (7.8.6); vacia = ataque basico
 * @param epica      solo en un Master: la que entrega al ser derrotado
 * @param ataque     sus formulas, de la vista por nivel de heroes: con ellas el
 *                   motor puede mantener la vida y la defensa de la semilla y del
 *                   escalon (nulas las tres = el motor usa las del catalogo)
 * @param origenDeEstrategia de donde salen sus rotaciones (HU-SIM-004); nulo si no tiene estrategia (ataque basico)
 * @param estrategiaId       el id de la estrategia predefinida, solo si {@code origenDeEstrategia} es PREDEFINIDA
 */
public record Rival(
        String nombre,
        TipoDeRival tipo,
        String prototipo,
        int nivel,
        int vida,
        int defensa,
        int poder,
        List<List<String>> rotaciones,
        Epica epica,
        Formula ataque,
        Formula dano,
        Formula sanar,
        OrigenDeEstrategia origenDeEstrategia,
        String estrategiaId) {

    /** Un rival sin formulas conocidas: el motor lo resuelve con las del catalogo. */
    public Rival(String nombre, TipoDeRival tipo, String prototipo, int nivel, int vida, int defensa, int poder,
                 List<List<String>> rotaciones, Epica epica) {
        this(nombre, tipo, prototipo, nivel, vida, defensa, poder, rotaciones, epica, null, null, null);
    }

    /** Un rival con formulas y sin origen declarado de su estrategia. */
    public Rival(String nombre, TipoDeRival tipo, String prototipo, int nivel, int vida, int defensa, int poder,
                 List<List<String>> rotaciones, Epica epica, Formula ataque, Formula dano, Formula sanar) {
        this(nombre, tipo, prototipo, nivel, vida, defensa, poder, rotaciones, epica, ataque, dano, sanar, null, null);
    }

    public Rival {
        Objects.requireNonNull(nombre, "Un rival necesita nombre.");
        Objects.requireNonNull(tipo, "Un rival necesita tipo.");
        Objects.requireNonNull(prototipo, "Un rival necesita prototipo.");
        if (vida < 1) {
            throw new IllegalArgumentException("Un rival sin vida no es un combate: «" + nombre + "».");
        }
        rotaciones = rotaciones == null ? List.of() : rotaciones;
        // Sin rotaciones no hay estrategia que trazar (juega el ataque basico); con ellas y sin origen declarado, son
        // las que trae la mision; el id solo lo tiene una estrategia predefinida.
        if (rotaciones.isEmpty()) {
            origenDeEstrategia = null;
        } else if (origenDeEstrategia == null) {
            origenDeEstrategia = OrigenDeEstrategia.MISION;
        }
        if (origenDeEstrategia != OrigenDeEstrategia.PREDEFINIDA) {
            estrategiaId = null;
        }
        if (tipo == TipoDeRival.MASTER) {
            Objects.requireNonNull(epica, "Un Master sin epica no es un Master: «" + nombre + "».");
        }
    }

    /** El mismo rival con otra vida, defensa, ataque y dano: el refuerzo de un Master ({@link RefuerzoDeMaster}). */
    Rival reforzado(int vida, int defensa, Formula ataque, Formula dano) {
        return new Rival(nombre, tipo, prototipo, nivel, vida, defensa, poder, rotaciones, epica, ataque, dano, sanar,
                origenDeEstrategia, estrategiaId);
    }
}
