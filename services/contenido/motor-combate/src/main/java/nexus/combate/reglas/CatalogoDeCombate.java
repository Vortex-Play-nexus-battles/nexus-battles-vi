package nexus.combate.reglas;

/**
 * Puerto hacia el catalogo de heroes: la ficha de un prototipo en un nivel.
 *
 * <p>El motor no guarda heroes ni reescribe la regla de escalado por nivel
 * (HU-HER-008): los pide. La implementacion HTTP vive fuera del dominio y lleva
 * una cache, porque la ficha de un prototipo en un nivel no cambia entre un
 * turno y el siguiente.
 */
public interface CatalogoDeCombate {

    /**
     * @throws nexus.combate.HeroeNoEncontradoException si el prototipo no existe
     * @throws nexus.combate.ClienteHeroesException     si el catalogo no responde
     */
    FichaDeCombate ficha(String prototipo, int nivel);
}
