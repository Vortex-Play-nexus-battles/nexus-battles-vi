package nexus.misiones.aplicacion;

import java.util.List;
import java.util.UUID;
import nexus.misiones.dominio.simulacion.Formula;

/**
 * Lo que misiones necesita del inventario (inventario.yaml 1.6.0). El
 * inventario es el dueno del heroe: quien lo tiene, si esta libre, que lleva
 * puesto, su nivel y su experiencia. Misiones nunca lee su base (regla 7).
 */
public interface InventarioDeHeroes {

    /**
     * {@code GET /api/v1/inventario/elementos/{id}} con la credencial de
     * misiones. Si el inventario responde 409 (inventario historico: el
     * producto no tiene id UUID y la consulta interna no lo describe), el heroe
     * se busca en la vitrina del jugador ({@code GET /api/v1/inventario/elementos}
     * con {@code X-User-Name}); por eso hace falta el jugador.
     *
     * @throws HeroeNoEncontrado si no existe (o no esta en la vitrina del jugador)
     */
    HeroeDelInventario consultar(String jugadorUid, String heroeId);

    /** Si lleva algo equipado ({@code GET .../heroes/{id}/equipamiento}). */
    boolean equipado(String jugadorUid, String heroeId);

    /**
     * Poder, vida y defensa con el equipamiento aplicado, en el nivel del heroe, y
     * sus formulas de ataque, dano y sanacion ({@code GET .../estadisticas}).
     */
    EstadisticasDelHeroe estadisticas(String jugadorUid, String heroeId);

    /**
     * Lo que lleva el heroe y las epicas del jugador, para que el motor aplique
     * sus efectos de combate: {@code GET .../heroes/{id}/equipamiento} y la
     * vitrina del jugador ({@code GET /api/v1/inventario/elementos}).
     *
     * @throws HeroeNoEncontrado si no existe o no es del jugador
     */
    EquipoDelHeroe equipo(String jugadorUid, String heroeId);

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

    /**
     * @param ataque formulas como datos; nulas si el inventario no las publica
     *               (un sanador no tiene ataque ni dano, un guerrero no sana)
     */
    record EstadisticasDelHeroe(int poder, int vida, int defensa, Formula ataque, Formula dano, Formula sanar) {

        public EstadisticasDelHeroe(int poder, int vida, int defensa) {
            this(poder, vida, defensa, null, null, null);
        }
    }

    /**
     * Los productos del catalogo de lo que lleva puesto el heroe (armas,
     * armaduras e items) y de las epicas disponibles del jugador. Son ids de
     * producto: el nombre, que es lo que entiende el motor, lo da el catalogo.
     */
    record EquipoDelHeroe(List<String> productosEquipados, List<String> productosDeEpicas) {

        public EquipoDelHeroe {
            productosEquipados = productosEquipados == null ? List.of() : List.copyOf(productosEquipados);
            productosDeEpicas = productosDeEpicas == null ? List.of() : List.copyOf(productosDeEpicas);
        }
    }

    record ProgresionDelHeroe(int nivel, double experiencia) {
    }

    record ProductoAEntregar(String productoId, int cantidad) {
    }
}
