package nexus.misiones.aplicacion;

import java.util.List;
import java.util.UUID;

/**
 * Lo que misiones necesita del inventario (inventario.yaml 1.6.0). El
 * inventario es el dueno del heroe: quien lo tiene, si esta libre, que lleva
 * puesto, su nivel y su experiencia. Misiones nunca lee su base (regla 7).
 */
public interface InventarioDeHeroes {

    /**
     * {@code GET /api/v1/inventario/elementos/{id}} con la credencial de
     * misiones.
     *
     * @throws HeroeNoEncontrado si no existe
     */
    HeroeDelInventario consultar(String heroeId);

    /** Si lleva algo equipado ({@code GET .../heroes/{id}/equipamiento}). */
    boolean equipado(String jugadorUid, String heroeId);

    /** Poder, vida y defensa con el equipamiento aplicado ({@code GET .../estadisticas}). */
    EstadisticasDelHeroe estadisticas(String jugadorUid, String heroeId);

    /**
     * {@code PUT .../bloqueo-mision}: desde aqui el heroe esta En mision.
     *
     * @throws HeroeOcupado      si ya esta en otra mision o en una subasta (409)
     * @throws HeroeNoEncontrado si no existe o no es del jugador
     */
    void bloquear(String heroeId, String jugadorUid, UUID ejecucionId);

    /**
     * {@code POST .../bloqueo-mision/{ejecucion}/liberacion}: libera al heroe y
     * le suma la experiencia en la misma escritura. Idempotente por estado.
     *
     * @throws RechazoDelServicio si el inventario lo rechaza de forma definitiva
     */
    ProgresionDelHeroe liberar(String heroeId, String jugadorUid, UUID ejecucionId, double experiencia);

    /**
     * {@code POST /api/v1/inventario/entregas}, origen MISION, idempotente por
     * {@code claveIdempotencia}.
     *
     * @throws RechazoDelServicio si el inventario lo rechaza de forma definitiva
     */
    void entregar(String jugadorUid, UUID ejecucionId, List<ProductoAEntregar> productos, String claveIdempotencia);

    record HeroeDelInventario(
            String id,
            String productoId,
            String propietarioUid,
            String tipo,
            String nombrePropio,
            boolean disponible,
            String subastaId,
            String ejecucionMisionId,
            Integer nivel,
            Double experiencia) {

        public boolean esHeroe() {
            return "HEROE".equals(tipo);
        }
    }

    record EstadisticasDelHeroe(int poder, int vida, int defensa) {
    }

    record ProgresionDelHeroe(int nivel, double experiencia) {
    }

    record ProductoAEntregar(String productoId, int cantidad) {
    }
}
