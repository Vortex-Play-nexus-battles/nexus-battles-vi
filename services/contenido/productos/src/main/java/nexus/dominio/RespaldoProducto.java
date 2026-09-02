package nexus.dominio;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Estado de un {@link Producto} justo antes de una modificacion (HU-PRD-003).
 *
 * DECISION EXPLICITA: no incluye quien hizo el cambio. Hoy SeguridadConfig
 * solo extrae roles del JWT (JwtGrantedAuthoritiesConverter), no el subject
 * como identidad de negocio, y ningun controller de este modulo lo propaga.
 * Agregar un campo "modificadoPor" sin esa identidad disponible obligaria a
 * inventar un valor - se deja fuera y documentado en vez de adivinar.
 */
@Document(collection = "productos_historico")
public record RespaldoProducto(

        @Id
        String id,

        String productoId,

        Producto estadoAnterior,

        Instant modificadoEn) {
}
