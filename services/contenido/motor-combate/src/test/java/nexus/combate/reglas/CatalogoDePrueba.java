package nexus.combate.reglas;

import nexus.combate.HeroeNoEncontradoException;

import java.util.List;
import java.util.Map;

/**
 * Catalogo de heroes en memoria con los datos de las Tablas 6 y 7, escalados
 * por nivel como lo hace el catalogo real (HU-HER-008: se multiplican poder,
 * vida, defensa y las BASES de las formulas; los dados no).
 */
public final class CatalogoDePrueba implements CatalogoDeCombate {

    private record Prototipo(String tipo, boolean sanador, int poder, int vida, int defensa,
                             Formula ataque, Formula dano, Formula sanar, List<AccionDelCatalogo> acciones,
                             String epica) {
    }

    private static AccionDelCatalogo accion(String nombre, Integer coste, int nivel) {
        return new AccionDelCatalogo(nombre, coste, coste == null, 1, nivel);
    }

    private static final Map<String, Prototipo> TABLA_6 = Map.of(
            "guerrero tanque", new Prototipo("Guerrero", false, 10, 44, 11,
                    new Formula(10, 1, 6), new Formula(0, 1, 4), null, List.of(
                    accion("Golpe con escudo", 2, 1), accion("Mano de piedra", 4, 4),
                    accion("Defensa feroz", 6, 8)), "Golpe de defensa"),
            "guerrero armas", new Prototipo("Guerrero", false, 8, 44, 11,
                    new Formula(10, 1, 6), new Formula(0, 1, 6), null, List.of(
                    accion("Embate sangriento", 4, 1), accion("Lanza de los dioses", 4, 4),
                    accion("Golpe de tormenta", 6, 8)), "Segundo impulso"),
            "mago fuego", new Prototipo("Mago", false, 8, 40, 10,
                    new Formula(10, 1, 8), new Formula(0, 1, 8), null, List.of(
                    accion("Misiles de magma", 2, 1), accion("Vulcano", 6, 4),
                    accion("Pare de fuego", 4, 8)), "Luz cegadora"),
            "mago hielo", new Prototipo("Mago", false, 10, 40, 10,
                    new Formula(10, 1, 8), new Formula(0, 1, 6), null, List.of(
                    accion("Lluvia de hielo", 2, 1), accion("Cono de hielo", 6, 4),
                    accion("Bola de hielo", 4, 8)), "Frio concentrado"),
            "picaro veneno", new Prototipo("Pícaro", false, 8, 36, 8,
                    new Formula(10, 1, 10), new Formula(0, 1, 6), null, List.of(
                    accion("Flor de loto", 2, 1), accion("Agonía", 4, 4),
                    accion("Piquete", 4, 8)), "Toma y lleva"),
            "picaro machete", new Prototipo("Pícaro", false, 8, 36, 8,
                    new Formula(10, 1, 10), new Formula(0, 1, 8), null, List.of(
                    accion("Cortada", 2, 1), accion("Machetazo", 4, 4),
                    accion("Planazo", 4, 8)), "Intimidación sangrienta"),
            "chaman", new Prototipo("Sanador", true, 10, 28, 4,
                    null, null, new Formula(6, 1, 6), List.of(
                    accion("Toque de la Vida", 2, 1), accion("Vínculo Natural", 4, 4),
                    accion("Canto del Bosque", 6, 8)), "Té changua"),
            "medico", new Prototipo("Sanador", true, 10, 28, 4,
                    null, null, new Formula(4, 1, 8), List.of(
                    accion("Curación Directa", 2, 1), accion("Neutralización de Efectos", 4, 4),
                    accion("Reanimación", null, 8)), "Reanimador 3000"));

    private int consultas;

    public CatalogoDePrueba() {
        // Un catalogo en memoria; lo usan tambien las pruebas de la capa web.
    }

    int consultas() {
        return consultas;
    }

    @Override
    public FichaDeCombate ficha(String prototipo, int nivel) {
        consultas++;
        Prototipo p = TABLA_6.get(Nombres.normalizar(prototipo));
        if (p == null) {
            throw new HeroeNoEncontradoException(prototipo, "No esta en el catalogo de prueba.");
        }
        Estadisticas escaladas = new Estadisticas(p.poder * nivel, p.vida * nivel, p.defensa * nivel,
                escalar(p.ataque, nivel), escalar(p.dano, nivel), escalar(p.sanar, nivel));
        return new FichaDeCombate(prototipo, p.tipo, p.sanador, nivel, escaladas, p.acciones, p.epica);
    }

    private static Formula escalar(Formula f, int nivel) {
        return f == null ? null : new Formula(f.base() * nivel, f.cantidadDados(), f.caras());
    }

    /** Un combatiente de nivel 1 a vida y poder llenos, sin equipo, en el equipo dado. */
    static Contendiente heroe(String id, String prototipo, Integer equipo) {
        return heroe(id, prototipo, equipo, 1);
    }

    static Contendiente heroe(String id, String prototipo, Integer equipo, int nivel) {
        return new Contendiente(id, equipo, prototipo, nivel, null, Integer.MAX_VALUE, Integer.MAX_VALUE, 0,
                Map.of(), List.of(), List.of(), List.of(), null);
    }
}
