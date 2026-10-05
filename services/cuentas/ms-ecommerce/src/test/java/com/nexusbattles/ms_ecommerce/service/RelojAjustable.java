package com.nexusbattles.ms_ecommerce.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Reloj que solo avanza cuando la prueba lo dice. */
final class RelojAjustable extends Clock {

    private Instant ahora;

    RelojAjustable(Instant inicio) {
        this.ahora = inicio;
    }

    void avanzar(Duration intervalo) {
        ahora = ahora.plus(intervalo);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zona) {
        return this;
    }

    @Override
    public Instant instant() {
        return ahora;
    }
}
