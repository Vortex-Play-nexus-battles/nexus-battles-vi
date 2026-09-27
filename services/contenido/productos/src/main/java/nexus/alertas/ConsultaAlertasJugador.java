package nexus.alertas;

import java.time.Instant;
import java.util.Objects;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "consultas_alertas_catalogo")
public record ConsultaAlertasJugador(
        @Id String jugadorId,
        Instant consultadoHasta) {

    public ConsultaAlertasJugador {
        if (jugadorId == null || jugadorId.isBlank()) {
            throw new IllegalArgumentException("jugadorId es obligatorio");
        }
        jugadorId = jugadorId.trim();
        consultadoHasta = Objects.requireNonNull(
                consultadoHasta,
                "La fecha de consulta es obligatoria");
    }
}
