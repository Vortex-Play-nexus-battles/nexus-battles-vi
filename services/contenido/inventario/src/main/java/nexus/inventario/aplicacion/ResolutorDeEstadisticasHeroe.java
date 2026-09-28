package nexus.inventario.aplicacion;

import nexus.inventario.dominio.EstadisticasHeroe;

/**
 * Puerto para resolver las estadisticas base de un prototipo de heroe en un
 * nivel. La implementacion HTTP real es {@link ResolutorDeEstadisticasHeroeHttp}
 * y consume {@code GET /api/v1/heroes/{nombre}/niveles/{nivel}} (heroes.yaml):
 * el escalado por nivel (HU-HER-008) es una regla del servicio de heroes y el
 * inventario no la reimplementa (B4).
 */
public interface ResolutorDeEstadisticasHeroe {

    /**
     * @param prototipo nombre del prototipo (ej. "Guerrero Tanque")
     * @param nivel     nivel del heroe, de 1 a 8 (lo guarda el inventario)
     */
    EstadisticasHeroe resolver(String prototipo, int nivel);

    /** Las estadisticas de nivel 1 (Tabla 6). */
    default EstadisticasHeroe resolver(String prototipo) {
        return resolver(prototipo, 1);
    }
}
