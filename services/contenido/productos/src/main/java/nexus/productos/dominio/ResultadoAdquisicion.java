package nexus.productos.dominio;

import java.util.Objects;

/** Respuesta explícita para que la capa de transporte pueda informar el rechazo. */
public record ResultadoAdquisicion(EstadoAdquisicion estado, String mensaje) {

    public ResultadoAdquisicion {
        Objects.requireNonNull(estado, "El estado de la adquisición es obligatorio");
        Objects.requireNonNull(mensaje, "El mensaje de la adquisición es obligatorio");
    }
}
