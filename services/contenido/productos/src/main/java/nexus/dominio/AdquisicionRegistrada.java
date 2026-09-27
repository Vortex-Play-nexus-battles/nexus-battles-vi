package nexus.dominio;

import java.time.Instant;

import nexus.productos.dominio.EstadoAdquisicion;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Una adquisicion ya pedida, guardada con su resultado — B4
 * ({@code POST /api/v1/productos/{id}/adquisiciones}, contrato 1.4.0).
 *
 * <p>El identificador ES la clave de idempotencia: el indice unico de
 * {@code _id} es el que impide que dos peticiones con la misma clave queden
 * registradas dos veces. Mientras {@code estado} es nulo la adquisicion esta en
 * curso (se registro la clave y aun no se sabe el resultado); un reintento la
 * termina sin volver a descontar, porque el descuento recuerda su clave en el
 * propio producto.
 *
 * @param clave        {@code Idempotency-Key} de quien reserva
 * @param productoId   producto para el que se uso la clave; la misma clave con
 *                     otro producto es un 409
 * @param estado       resultado, o nulo mientras esta en curso
 * @param mensaje      el mensaje de ese resultado
 * @param solicitante  {@code azp} del servicio que la pidio, para la bitacora
 * @param registradaEn cuando se registro la clave
 */
@Document(collection = "adquisiciones")
public record AdquisicionRegistrada(
        @Id String clave,
        String productoId,
        EstadoAdquisicion estado,
        String mensaje,
        String solicitante,
        Instant registradaEn) {

    public boolean enCurso() {
        return estado == null;
    }
}
