package nexus.api;

import java.time.Instant;

import nexus.auditoria.OperacionAdministrativa;
import nexus.auditoria.TipoOperacion;

/**
 * Una entrada del historial de un banner para la administración del catálogo —
 * SA-18, productos.yaml 1.12.0.
 *
 * <p>Sin la dirección IP: el historial lo ve también un ADMINISTRADOR, y la IP
 * de quien actuó solo la ve el Super Administrador en la bitácora central
 * (§7.3.5, «accesible solo para Super Administradores»).
 */
public record OperacionDelCatalogoVista(
        String id,
        Instant fecha,
        String autor,
        TipoOperacion tipo,
        String valorAnterior,
        String valorNuevo,
        String motivo,
        String descripcion) {

        public static OperacionDelCatalogoVista de(OperacionAdministrativa operacion) {
                return new OperacionDelCatalogoVista(
                                operacion.id(),
                                operacion.ocurridaEn(),
                                operacion.autor(),
                                operacion.tipo(),
                                operacion.valorAnterior(),
                                operacion.valorNuevo(),
                                operacion.motivo(),
                                operacion.descripcion());
        }
}
