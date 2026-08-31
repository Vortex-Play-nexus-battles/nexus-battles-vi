package nexus.productos.dominio;

import java.util.Objects;

public record ResultadoAdquisicion(EstadoAdquisicion estado, String mensaje) {

    public ResultadoAdquisicion {
        Objects.requireNonNull(estado, "El estado de la adquisición es obligatorio");
        Objects.requireNonNull(mensaje, "El mensaje de la adquisición es obligatorio");
    }
}
