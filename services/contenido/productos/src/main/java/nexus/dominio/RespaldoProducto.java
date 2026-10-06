package nexus.dominio;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Entrada inmutable del historial de un producto (HU-PRD-003/HU-PRD-007).
 *
 * <p>{@code estadoAnterior} es también el punto de recuperación y
 * {@code estadoAplicado} permite auditar exactamente qué cambió. En una
 * creación no existe estado anterior. Una reversión agrega otra entrada en
 * vez de modificar o borrar la original.
 *
 * <p>{@code autor} es el identificador estable (claim {@code uid}) del
 * administrador que hizo el cambio — B4. Hasta entonces el respaldo no decia
 * quien habia modificado nada porque ningun controlador propagaba la identidad
 * del token; ahora la pasa el controlador desde la autenticacion (principal de
 * {@code ConversorRolesJwt}: el {@code uid}, o el {@code sub} si el token no lo
 * trae). Los respaldos anteriores a B4 no tienen autor.
 */
@Document(collection = "productos_historico")
public record RespaldoProducto(

        @Id
        String id,

        String productoId,

        Producto estadoAnterior,

        Producto estadoAplicado,

        Instant modificadoEn,

        String autor,

        TipoCambioProducto tipoCambio,

        String reversionDe) {

        /** Compatibilidad con los respaldos creados antes de HU-PRD-007. */
        public RespaldoProducto(
                        String id,
                        String productoId,
                        Producto estadoAnterior,
                        Instant modificadoEn,
                        String autor) {
                this(
                                id,
                                productoId,
                                estadoAnterior,
                                null,
                                modificadoEn,
                                autor,
                                TipoCambioProducto.MODIFICACION,
                                null);
        }

        public TipoCambioProducto tipoCambioNormalizado() {
                return tipoCambio == null ? TipoCambioProducto.MODIFICACION : tipoCambio;
        }
}
