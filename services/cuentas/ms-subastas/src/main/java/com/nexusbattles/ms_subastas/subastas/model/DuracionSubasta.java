package com.nexusbattles.ms_subastas.subastas.model;

import java.time.Duration;

public enum DuracionSubasta {
    H24(Duration.ofHours(24)),
    H48(Duration.ofHours(48));

    private final Duration duracion;

    DuracionSubasta(Duration duracion) { this.duracion = duracion; }

    public Duration duracion() { return duracion; }
}
