package com.nexusbattles.ms_subastas.subastas.model;

import java.time.Duration;

public enum DuracionSubasta {
    H24(Duration.ofHours(24)),
    H48(Duration.ofHours(48));

    private final Duration duracion;

    DuracionSubasta(Duration duracion) { this.duracion = duracion; }

    public Duration duracion() { return duracion; }

    @com.fasterxml.jackson.annotation.JsonValue
    public String valorJson() { return this == H24 ? "24H" : "48H"; }

    @com.fasterxml.jackson.annotation.JsonCreator
    public static DuracionSubasta desdeJson(String valor) {
        return switch (valor) {
            case "24H" -> H24;
            case "48H" -> H48;
            default -> throw new IllegalArgumentException("La duracion debe ser 24H o 48H");
        };
    }
}
