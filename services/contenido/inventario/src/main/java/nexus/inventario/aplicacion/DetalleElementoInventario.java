package nexus.inventario.aplicacion;

import java.util.UUID;
import nexus.inventario.dominio.TipoElementoInventario;

/**
 * Datos estables que Inventario publica para integraciones entre servicios.
 *
 * <p>1.6.0 (B9): tambien el tipo, el nombre propio, la progresion del heroe y
 * la ejecucion de mision que lo bloquea, para que misiones compruebe antes de
 * matricular que es un heroe, que es del jugador y que esta libre.
 */
public record DetalleElementoInventario(
        String elementoId,
        UUID productoId,
        UUID propietarioUid,
        boolean enUso,
        boolean disponible,
        UUID subastaId,
        TipoElementoInventario tipo,
        String nombrePropio,
        Integer nivel,
        Double experiencia,
        UUID ejecucionMisionId) {

    public DetalleElementoInventario(String elementoId, UUID productoId, UUID propietarioUid, boolean enUso,
                                     boolean disponible, UUID subastaId) {
        this(elementoId, productoId, propietarioUid, enUso, disponible, subastaId, null, null, null, null, null);
    }
}
