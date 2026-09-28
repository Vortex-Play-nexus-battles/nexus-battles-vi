package nexus.combate.api;

import nexus.combate.reglas.Evento;
import nexus.combate.reglas.ResultadoDeTurno;

import java.util.List;

/**
 * Espejo del esquema {@code ResultadoDeTurno} de {@code motor-combate.yaml} 1.2.0:
 * el estado de todos al empezar el turno de {@code combatiente}.
 */
public record RespuestaDeTurno(String combatiente, List<RespuestaDeAccion.Afectado> afectados,
                               List<Evento> eventos, List<EstadoDeCombatiente> combatientes) {

    static RespuestaDeTurno de(ResultadoDeTurno r) {
        return new RespuestaDeTurno(r.combatiente(), RespuestaDeAccion.Afectado.de(r.afectados()), r.eventos(),
                TraductorDeCombate.deDominio(r.combatientes(), r.acciones(), r.recargas()));
    }
}
