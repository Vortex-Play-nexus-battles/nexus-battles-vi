package nexus.combate.reglas;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Lo que el catalogo de heroes sabe de un prototipo en un nivel: la vista por
 * nivel ({@code GET /heroes/{nombre}/niveles/{nivel}}) mas las tres acciones de
 * la ficha, con su nivel de desbloqueo.
 *
 * @param prototipo     nombre del catalogo
 * @param tipo          Guerrero, Mago, Picaro o Sanador; decide si su dano es
 *                      fisico o magico (Defensa feroz, D-B7-05)
 * @param esSanador     Chaman o Medico
 * @param nivel         nivel al que corresponden las estadisticas
 * @param estadisticas  escaladas por nivel (§6.1.1), sin equipo
 * @param acciones      las tres de la Tabla 7, en su orden
 * @param epicaAfin     nombre de la epica de la Tabla 20 afin a este prototipo
 */
public record FichaDeCombate(String prototipo, String tipo, boolean esSanador, int nivel,
                             Estadisticas estadisticas, List<AccionDelCatalogo> acciones,
                             String epicaAfin) {

    public FichaDeCombate {
        Objects.requireNonNull(prototipo, "La ficha necesita su prototipo.");
        Objects.requireNonNull(estadisticas, "La ficha necesita sus estadisticas.");
        acciones = acciones == null ? List.of() : List.copyOf(acciones);
    }

    /** Multiplicador de efecto de las acciones especiales: el nivel (HU-HER-007, §6.1.2). */
    public int multiplicador() {
        return nivel;
    }

    /** El dano de este heroe es magico si es un Mago (D-B7-05). */
    public boolean danoMagico() {
        return tipo != null && Nombres.normalizar(tipo).equals("mago");
    }

    /** La accion de la Tabla 7 con ese nombre, tolerando tildes y mayusculas. */
    public Optional<AccionDelCatalogo> accion(String nombre) {
        String buscado = Nombres.normalizar(nombre);
        return acciones.stream().filter(a -> Nombres.normalizar(a.nombre()).equals(buscado)).findFirst();
    }
}
