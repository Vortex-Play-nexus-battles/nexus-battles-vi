package nexus.inventario.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import nexus.inventario.aplicacion.SolicitudDeEntrega;
import nexus.inventario.dominio.LineaDeEntrega;
import nexus.inventario.dominio.OrigenDeEntrega;

/**
 * Cuerpo de {@code POST /api/v1/inventario/entregas} — esquema
 * {@code SolicitudDeEntrega} del contrato de inventario (B4).
 *
 * <p>El jugador que recibe viaja en el cuerpo, no en el token: quien llama es
 * un servicio o un administrador que actua por el (como {@code nuevoPropietarioUid}
 * en la transferencia de subastas). El tipo, el nombre y la parte de cada
 * elemento no se aceptan de quien entrega: los decide el catalogo.
 */
public record EntregaRequest(
        @NotNull UUID uid,
        @NotNull OrigenDeEntrega origen,
        @NotBlank @Size(max = 120) String referencia,
        @NotNull @Size(min = 1, max = 50) List<@NotNull @Valid Linea> productos) {

    /** Un producto y cuantas unidades de el. */
    public record Linea(@NotBlank String productoId, @NotNull @Min(1) @Max(20) Integer cantidad) {
    }

    SolicitudDeEntrega aSolicitud() {
        return new SolicitudDeEntrega(
                uid,
                origen,
                referencia.strip(),
                productos.stream().map(linea -> new LineaDeEntrega(linea.productoId().strip(), linea.cantidad())).toList());
    }
}
