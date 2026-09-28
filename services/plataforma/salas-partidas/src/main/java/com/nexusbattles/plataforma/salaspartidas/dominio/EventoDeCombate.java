package com.nexusbattles.plataforma.salaspartidas.dominio;

import java.util.UUID;

/**
 * Algo que paso en una resolucion del motor — esquema {@code Evento} de
 * {@code motor-combate.yaml} 1.2.0: un golpe, una sanacion, un efecto que se
 * aplico o termino, un sangrado al empezar un turno...
 *
 * @param tipo        el tipo de evento del motor ({@code DANO_POR_TURNO}...), como texto
 * @param combatiente a quien le paso
 * @param origen      quien lo causo, o {@code null}
 * @param efecto      nombre del efecto, la accion o el objeto, o {@code null}
 * @param cantidad    vida, poder o valor que movio, o {@code null}
 */
public record EventoDeCombate(String tipo, UUID combatiente, UUID origen, String efecto, Integer cantidad) {

    /** Un efecto por turno que movio vida al empezar un turno. */
    public boolean esEfectoPorTurno() {
        return "DANO_POR_TURNO".equals(tipo) || "SANACION_POR_TURNO".equals(tipo);
    }
}
