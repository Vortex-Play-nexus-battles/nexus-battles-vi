package com.nexusbattles.ms_subastas.pujas.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Reloj de prueba que avanza a voluntad, para no depender de Thread.sleep al validar el intervalo minimo entre pujas. */
public class MutableClock extends Clock {

    private Instant instante;
    private final ZoneId zona;

    public MutableClock(Instant inicial) {
        this(inicial, ZoneOffset.UTC);
    }

    public MutableClock(Instant inicial, ZoneId zona) {
        this.instante = inicial;
        this.zona = zona;
    }

    public void avanzar(Duration duracion) {
        instante = instante.plus(duracion);
    }

    @Override
    public ZoneId getZone() {
        return zona;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(instante, zone);
    }

    @Override
    public Instant instant() {
        return instante;
    }
}
