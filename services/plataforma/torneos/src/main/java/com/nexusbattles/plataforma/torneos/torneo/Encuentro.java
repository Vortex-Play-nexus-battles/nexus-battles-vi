package com.nexusbattles.plataforma.torneos.torneo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Un encuentro del arbol (RF-TOR-004). El numero es el de la ficha: 1-6 y 11
 * en la llave de ganadores, 7-10, 12 y 13 en la de secundarios, 14 la final.
 */
@Entity
@Table(name = "encuentros")
public class Encuentro {

    public enum Llave { GANADORES, SECUNDARIOS, FINAL }

    public enum Estado { PENDIENTE, LISTO, JUGADO }

    /** Numero de la final; no esta en la ficha con numero, se le da el siguiente. */
    public static final int FINAL = 14;

    @Id
    private UUID id;

    @Column(name = "torneo_id", nullable = false)
    private UUID torneoId;

    @Column(nullable = false)
    private int numero;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Llave llave;

    @Column(nullable = false)
    private int ronda;

    @Column(name = "equipo_a")
    private UUID equipoA;

    @Column(name = "equipo_b")
    private UUID equipoB;

    private UUID ganador;

    @Column(name = "partida_id")
    private UUID partidaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado;

    @Column(name = "registrado_por", length = 100)
    private String registradoPor;

    @Column(length = 500)
    private String motivo;

    @Column(name = "jugado_en")
    private OffsetDateTime jugadoEn;

    protected Encuentro() {
    }

    public Encuentro(UUID id, UUID torneoId, int numero, Llave llave, int ronda) {
        this.id = Objects.requireNonNull(id);
        this.torneoId = Objects.requireNonNull(torneoId);
        this.numero = numero;
        this.llave = Objects.requireNonNull(llave);
        this.ronda = ronda;
        this.estado = Estado.PENDIENTE;
    }

    void ponerEnA(UUID equipo) {
        this.equipoA = equipo;
        actualizarEstado();
    }

    void ponerEnB(UUID equipo) {
        this.equipoB = equipo;
        actualizarEstado();
    }

    private void actualizarEstado() {
        if (estado == Estado.PENDIENTE && equipoA != null && equipoB != null) {
            estado = Estado.LISTO;
        }
    }

    public boolean participa(UUID equipo) {
        return equipo != null && (equipo.equals(equipoA) || equipo.equals(equipoB));
    }

    public boolean listo() {
        return estado == Estado.LISTO;
    }

    public UUID perdedor() {
        if (ganador == null) {
            return null;
        }
        return ganador.equals(equipoA) ? equipoB : equipoA;
    }

    void jugar(UUID ganador, UUID partidaId, String registradoPor, String motivo, OffsetDateTime ahora) {
        if (!listo()) {
            throw new IllegalStateException("el encuentro " + numero + " no esta listo para jugarse");
        }
        if (!participa(ganador)) {
            throw new IllegalArgumentException("el ganador no juega el encuentro " + numero);
        }
        this.ganador = ganador;
        this.partidaId = partidaId;
        this.registradoPor = registradoPor;
        this.motivo = motivo;
        this.jugadoEn = ahora;
        this.estado = Estado.JUGADO;
    }

    public UUID id() { return id; }
    public UUID torneoId() { return torneoId; }
    public int numero() { return numero; }
    public Llave llave() { return llave; }
    public int ronda() { return ronda; }
    public UUID equipoA() { return equipoA; }
    public UUID equipoB() { return equipoB; }
    public UUID ganador() { return ganador; }
    public UUID partidaId() { return partidaId; }
    public Estado estado() { return estado; }
    public String registradoPor() { return registradoPor; }
    public String motivo() { return motivo; }
    public OffsetDateTime jugadoEn() { return jugadoEn; }
}
