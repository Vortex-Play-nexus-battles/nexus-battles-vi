package nexus.misiones.dominio.simulacion;

/**
 * La experiencia por derrotar a un enemigo no jugador: 10 x 1,2^(1d8)
 * (seccion 6.1.1). El dado lo tira la simulacion; el valor lo da el servicio
 * de heroes ({@code GET /api/v1/progresion/experiencia-por-enemigo/{dado}}),
 * que es donde vive la regla.
 */
@FunctionalInterface
public interface TablaDeExperiencia {

    double porEnemigoDerrotado(int dado);
}
