package com.nexusbattles.plataforma.salaspartidas.persistencia;

import com.nexusbattles.plataforma.salaspartidas.dominio.RecompensaDePartida;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Fila de {@code recompensas_de_partida} (V11) — HU-JUE-012, CA-05.
 *
 * <p>Gemela de {@link LiquidacionEntidad}: separada del dominio porque JPA
 * necesita constructor vacio y campos mutables, y el dominio no.
 */
@Entity
@Table(name = "recompensas_de_partida")
class RecompensaEntidad {

    @Id
    @Column(name = "id_partida")
    private UUID idPartida;

    @Column(name = "id_sala", nullable = false)
    private UUID idSala;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecompensaDePartida.Estado estado;

    @Column(nullable = false)
    private int intentos;

    @Column(name = "ultimo_error", length = 500)
    private String ultimoError;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn;

    @Column(name = "actualizada_en", nullable = false)
    private Instant actualizadaEn;

    /** Exigido por JPA. No usar desde el codigo. */
    protected RecompensaEntidad() {
    }

    static RecompensaEntidad desde(RecompensaDePartida recompensa) {
        RecompensaEntidad fila = new RecompensaEntidad();
        fila.idPartida = recompensa.idPartida();
        fila.idSala = recompensa.idSala();
        fila.estado = recompensa.estado();
        fila.intentos = recompensa.intentos();
        fila.ultimoError = recompensa.ultimoError();
        fila.creadaEn = recompensa.creadaEn();
        fila.actualizadaEn = recompensa.actualizadaEn();
        return fila;
    }

    RecompensaDePartida aDominio() {
        return RecompensaDePartida.rehidratar(idPartida, idSala, estado, intentos, ultimoError,
                creadaEn, actualizadaEn);
    }
}
