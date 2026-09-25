package com.nexusbattles.plataforma.correo;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Un reloj que la prueba adelanta a mano: los reintentos esperan de 30 s a una
 * hora, y ninguna prueba deberia esperarlos de verdad.
 */
final class RelojAjustable extends Clock {

    private volatile Instant ahora;

    RelojAjustable(Instant inicio) {
        this.ahora = inicio;
    }

    void adelantar(Duration cuanto) {
        ahora = ahora.plus(cuanto);
    }

    @Override
    public Instant instant() {
        return ahora;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zona) {
        return this;
    }
}
