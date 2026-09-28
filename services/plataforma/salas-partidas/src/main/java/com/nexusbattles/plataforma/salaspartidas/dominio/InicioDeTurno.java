package com.nexusbattles.plataforma.salaspartidas.dominio;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Lo que devolvio el motor al empezar el turno de un combatiente —
 * {@code ResultadoDeTurno} de {@code motor-combate.yaml} 1.2.0: terminan sus
 * protecciones, actuan sus efectos por turno y recupera dos de poder (§6.1.1).
 *
 * @param combatiente  quien empieza su turno
 * @param afectados    quienes cambiaron de vida (un sangrado, una sanacion por turno)
 * @param eventos      todo lo que paso
 * @param combatientes el estado de todos al empezar el turno
 */
public record InicioDeTurno(UUID combatiente, List<ResolucionDeAccion.Afectado> afectados,
                            List<EventoDeCombate> eventos, List<CombatienteResuelto> combatientes) {

    public InicioDeTurno {
        Objects.requireNonNull(combatiente, "Un turno empieza para alguien.");
        afectados = afectados == null ? List.of() : List.copyOf(afectados);
        eventos = eventos == null ? List.of() : List.copyOf(eventos);
        combatientes = combatientes == null ? List.of() : List.copyOf(combatientes);
    }
}
