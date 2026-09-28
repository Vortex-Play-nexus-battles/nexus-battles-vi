package com.nexusbattles.plataforma.salaspartidas.dominio;

import java.util.Objects;
import java.util.UUID;

/**
 * Como quedo un participante tras una respuesta del motor de combate (B7): su
 * vida, su vida maxima en su nivel y su estado de combate. La partida lo guarda
 * tal cual con {@link Partida#aplicarCombate}.
 *
 * @param id         el participante ({@code idJugador}, o el de la maquina)
 * @param vidaActual vida ahora
 * @param vidaMaxima vida maxima en su nivel, con su equipo
 * @param estado     poder, cargas, efectos, acciones...
 */
public record CombatienteResuelto(UUID id, int vidaActual, int vidaMaxima, EstadoDeCombate estado) {

    public CombatienteResuelto {
        Objects.requireNonNull(id, "Un combatiente resuelto es un participante concreto.");
        Objects.requireNonNull(estado, "El motor siempre devuelve el estado de cada combatiente.");
    }
}
