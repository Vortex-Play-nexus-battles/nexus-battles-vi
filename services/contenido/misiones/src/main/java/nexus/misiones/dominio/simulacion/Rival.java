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
        Formula sanar) {

    /** Un rival sin formulas conocidas: el motor lo resuelve con las del catalogo. */
    public Rival(String nombre, TipoDeRival tipo, String prototipo, int nivel, int vida, int defensa, int poder,
                 List<List<String>> rotaciones, Epica epica) {
        this(nombre, tipo, prototipo, nivel, vida, defensa, poder, rotaciones, epica, null, null, null);
    }

    public Rival {
        Objects.requireNonNull(nombre, "Un rival necesita nombre.");
        Objects.requireNonNull(tipo, "Un rival necesita tipo.");
        Objects.requireNonNull(prototipo, "Un rival necesita prototipo.");
        if (vida < 1) {
            throw new IllegalArgumentException("Un rival sin vida no es un combate: «" + nombre + "».");
        }
        rotaciones = rotaciones == null ? List.of() : rotaciones;
        if (tipo == TipoDeRival.MASTER) {
            Objects.requireNonNull(epica, "Un Master sin epica no es un Master: «" + nombre + "».");
        }
    }
}
