package com.nexusbattles.ms_ecommerce.compra;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * El reloj del servicio en las pruebas de la compra: arranca en la hora real y
 * solo avanza cuando la prueba lo dice (caducar una orden PENDIENTE, vaciar las
 * copias de 30 s del catalogo entre dos pruebas). Seguro entre hilos: la
 * compra concurrente lo lee desde varios.
 */
final class RelojDePrueba extends Clock {

    private final AtomicReference<Instant> ahora = new AtomicReference<>(Instant.now());

    void avanzar(Duration intervalo) {
        ahora.updateAndGet(actual -> actual.plus(intervalo));
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
        return ahora.get();
    }
}
