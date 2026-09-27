package com.nexusbattles.plataforma.salaspartidas.dominio;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Lo que devolvio el motor de combate al resolver una accion —
 * {@code ResultadoDeAccion} de {@code motor-combate.yaml} 1.2.0.
 *
 * @param accion          la pedida; con la decision de la maquina, la que eligio el motor
 * @param accionEjecutada la que se jugo: distinta si falto poder (§6.1.1)
 * @param enValorBase     falto poder y el turno se jugo con el valor base
 * @param ejecutor        quien actuo
 * @param objetivo        a quien, o {@code null}
 * @param tipo            ATAQUE, DEFENSA, SANACION...
 * @param esEpica         epica de la Tabla 20
 * @param potenciada      epica jugada por su heroe afin
 * @param ataque          la tirada, si golpeo
 * @param afectados       quienes cambiaron de vida
 * @param eventos         todo lo que paso
 * @param combatientes    el estado nuevo de todos
 */
public record ResolucionDeAccion(String accion, String accionEjecutada, boolean enValorBase, UUID ejecutor,
                                 UUID objetivo, String tipo, boolean esEpica, boolean potenciada, Golpe ataque,
                                 List<Afectado> afectados, List<EventoDeCombate> eventos,
                                 List<CombatienteResuelto> combatientes) {

    public ResolucionDeAccion {
        Objects.requireNonNull(accionEjecutada, "El motor siempre dice que accion se jugo.");
        Objects.requireNonNull(ejecutor, "Una accion tiene quien la ejecuta.");
        afectados = afectados == null ? List.of() : List.copyOf(afectados);
        eventos = eventos == null ? List.of() : List.copyOf(eventos);
        combatientes = combatientes == null ? List.of() : List.copyOf(combatientes);
    }

    /**
     * La tirada de un ataque — {@code DetalleDeAtaque} del motor.
     *
     * @param categoria la categoria de la Tabla 22 que salio ({@code CAUSAR_DANO}...)
     */
    public record Golpe(int ataqueResuelto, int defensaObjetivo, boolean acierta, String categoria,
                        Integer indiceTabla, Integer porcentajeDano, int danoBase, int danoAplicado) {
    }

    /** Alguien que acabo con otra vida — {@code Afectado} del motor. */
    public record Afectado(UUID id, int vidaAntes, int vidaDespues, int diferencia) {
    }
}
