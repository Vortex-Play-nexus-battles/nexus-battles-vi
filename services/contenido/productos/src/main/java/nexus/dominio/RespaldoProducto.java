package nexus.dominio;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Estado de un {@link Producto} justo antes de una modificacion (HU-PRD-003).
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

        Instant modificadoEn,

        String autor) {
}
