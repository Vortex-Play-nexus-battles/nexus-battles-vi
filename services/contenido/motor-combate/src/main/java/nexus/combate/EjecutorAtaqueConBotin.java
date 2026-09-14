package nexus.combate;

import java.util.Objects;
import java.util.Optional;

public final class EjecutorAtaqueConBotin {

    private final ProcesadorBotin procesadorBotin;

    public EjecutorAtaqueConBotin(ProcesadorBotin procesadorBotin) {
        this.procesadorBotin = Objects.requireNonNull(
                procesadorBotin,
                "El procesador de botin es obligatorio");
    }

    public Optional<ResultadoBotin> ejecutar(
            String accionId,
            Partida partida,
            ControlAccionesTurno controlTurnos,
            ParticipanteBotin atacante,
            ParticipanteBotin objetivo,
            ResolucionAtaque resolucion) {
        exigirTexto(accionId, "accionId");
        Objects.requireNonNull(partida, "La partida es obligatoria");
        Objects.requireNonNull(controlTurnos, "El control de turnos es obligatorio");
        Objects.requireNonNull(atacante, "El atacante es obligatorio");
        Objects.requireNonNull(objetivo, "El objetivo es obligatorio");
        Objects.requireNonNull(resolucion, "La resolucion es obligatoria");

        Combatiente antes = partida.combatiente(objetivo.combatienteId());
        partida.ejecutarAtaque(
                controlTurnos,
                atacante.combatienteId(),
                objetivo.combatienteId(),
                resolucion);
        Combatiente despues = partida.combatiente(objetivo.combatienteId());

        if (antes.participa() && !despues.participa() && despues.vida() == 0) {
            return Optional.of(procesadorBotin.procesar(new DerrotaEnemigo(
                    accionId,
                    atacante.propietarioId(),
                    objetivo.propietarioId(),
                    objetivo.heroeInventarioId())));
        }
        return Optional.empty();
    }

    private static void exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " es obligatorio");
        }
    }
}
