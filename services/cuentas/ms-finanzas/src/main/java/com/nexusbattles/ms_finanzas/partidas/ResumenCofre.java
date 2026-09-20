package com.nexusbattles.ms_finanzas.partidas;

import java.time.Instant;
import java.util.UUID;

/**
 * Proyección de lectura para la pantalla "Mis cofres". No expone el
 * {@code uidJugador} (redundante — el llamador ya se autenticó como ese
 * jugador) ni la semana ISO en la que se ganó (dato interno).
 */
public record ResumenCofre(
        UUID id,
        String contenido,
        Instant entregadoEn) {

    public static ResumenCofre desde(CofreEntregado cofre) {
        return new ResumenCofre(cofre.getId(), cofre.getContenido(), cofre.getEntregadoEn());
    }
}
