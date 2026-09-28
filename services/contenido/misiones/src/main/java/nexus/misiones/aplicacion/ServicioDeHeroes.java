package nexus.misiones.aplicacion;

import java.util.List;
import nexus.misiones.dominio.simulacion.DecisorDeTurno;
import nexus.misiones.dominio.simulacion.TablaDeExperiencia;

/**
 * Lo que misiones necesita del servicio de heroes (heroes.yaml): las reglas
 * del juego como servicio, para no reimplementarlas aqui.
 */
public interface ServicioDeHeroes extends DecisorDeTurno, TablaDeExperiencia {

    /** {@code POST /api/v1/estrategias/validacion} (HU-SIM-001, 7.8.5). */
    VeredictoDeEstrategia validarEstrategia(String prototipo, int nivel, List<List<String>> rotaciones);

    /**
     * {@code POST /api/v1/equipos/validacion} con un solo heroe: la regla del
     * enfrentamiento individual (RC-09: por defecto un sanador no pelea solo).
     */
    VeredictoDeComposicion validarIndividual(String prototipo);

    /** {@code GET /api/v1/heroes/{prototipo}/niveles/{nivel}}: estadisticas de un enemigo. */
    EstadisticasDeNivel enNivel(String prototipo, int nivel);

    /**
     * @param rotaciones las del veredicto, con los nombres exactos de la Tabla 7
     */
    record VeredictoDeEstrategia(boolean valida, String motivo, List<List<String>> rotaciones,
                                 List<String> habilidadesValidas) {
    }

    record VeredictoDeComposicion(boolean valida, String motivo) {
    }

    record EstadisticasDeNivel(int poder, int vida, int defensa) {
    }
}
