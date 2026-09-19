package com.nexusbattles.ms_finanzas.partidas;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Clave compuesta del contador semanal: (uid del jugador, semana ISO).
 * Se materializa como {@code @Embeddable} para que JPA la use como PK
 * compuesta en {@link ContadorSemanalCofre}.
 */
@Embeddable
public class ContadorSemanalCofreId implements Serializable {

    @Column(name = "uid_jugador", length = 64, nullable = false)
    private String uidJugador;

    @Column(name = "semana_iso", length = 10, nullable = false)
    private String semanaIso;

    public ContadorSemanalCofreId() { }

    public ContadorSemanalCofreId(String uidJugador, String semanaIso) {
        this.uidJugador = uidJugador;
        this.semanaIso = semanaIso;
    }

    public String getUidJugador() { return uidJugador; }
    public String getSemanaIso() { return semanaIso; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContadorSemanalCofreId that)) return false;
        return Objects.equals(uidJugador, that.uidJugador)
                && Objects.equals(semanaIso, that.semanaIso);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uidJugador, semanaIso);
    }
}
