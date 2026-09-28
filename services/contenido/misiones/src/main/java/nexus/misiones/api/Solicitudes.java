package nexus.misiones.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import nexus.misiones.dominio.Escalon;

/** Los cuerpos que recibe el servicio (misiones.yaml), con sus limites del contrato. */
final class Solicitudes {

    private Solicitudes() {
    }

    /** {@code SolicitudDeMatricula}. Sin rotaciones: la guardada, o el ataque basico. */
    record Matricula(
            @NotBlank @Size(max = 100) String heroeId,
            @Size(max = 3) List<@Valid @NotNull Rotacion> rotaciones,
            Escalon escalon) {
    }

    /** {@code SolicitudDeEstrategia}. */
    record Estrategia(@NotNull @Size(max = 3) List<@Valid @NotNull Rotacion> rotaciones) {
    }

    /** {@code RotacionSolicitada}: la secuencia de habilidades de una rotacion. */
    record Rotacion(@NotEmpty @Size(max = 10) List<@NotBlank @Size(max = 60) String> pasos) {
    }

    static List<List<String>> comoListas(List<Rotacion> rotaciones) {
        return rotaciones == null ? null : rotaciones.stream().map(r -> List.copyOf(r.pasos())).toList();
    }
}
