package nexus.misiones.dominio;

import java.util.Optional;

/**
 * Las estrategias predefinidas de los enemigos (HU-SIM-004): una por prototipo y tramo de nivel.
 */
@FunctionalInterface
public interface CatalogoDeEstrategiasDeEnemigos {

    /**
     * @return la estrategia del tramo al que pertenece el nivel (1 a 3, 4 a 7, 8 en adelante), o vacio si ese
     *         prototipo no tiene una valida para ese tramo: no se usa la del tramo anterior, porque le faltaria
     *         una habilidad y el enemigo pelearia distinto de como se diseno
     */
    Optional<EstrategiaPredefinida> para(String prototipo, int nivel);
}
