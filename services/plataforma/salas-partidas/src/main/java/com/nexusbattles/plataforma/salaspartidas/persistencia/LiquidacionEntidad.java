package com.nexusbattles.plataforma.salaspartidas.persistencia;

import com.nexusbattles.plataforma.salaspartidas.dominio.LiquidacionDeApuesta;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Fila de {@code liquidaciones_de_apuesta} (V9) — HU-JUE-014, CA-06.
 *
 * <p>Separada del dominio por el mismo motivo que {@link SalaEntidad}: JPA
 * necesita constructor vacio y campos mutables, y el dominio no.
 */
@Entity
@Table(name = "liquidaciones_de_apuesta")
class LiquidacionEntidad {

    @Id
    @Column(name = "id_partida")
    private UUID idPartida;

    @Column(name = "id_sala", nullable = false)
    private UUID idSala;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LiquidacionDeApuesta.Estado estado;

    @Column(nullable = false)
    private int intentos;

    @Column(name = "ultimo_error", length = 500)
    private String ultimoError;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn;

    @Column(name = "actualizada_en", nullable = false)
    private Instant actualizadaEn;

    /** Exigido por JPA. No usar desde el codigo. */
    protected LiquidacionEntidad() {
    }

    static LiquidacionEntidad desde(LiquidacionDeApuesta liquidacion) {
        LiquidacionEntidad fila = new LiquidacionEntidad();
        fila.idPartida = liquidacion.idPartida();
        fila.idSala = liquidacion.idSala();
        fila.estado = liquidacion.estado();
        fila.intentos = liquidacion.intentos();
        fila.ultimoError = liquidacion.ultimoError();
        fila.creadaEn = liquidacion.creadaEn();
        fila.actualizadaEn = liquidacion.actualizadaEn();
        return fila;
    }

    LiquidacionDeApuesta aDominio() {
        return LiquidacionDeApuesta.rehidratar(idPartida, idSala, estado, intentos, ultimoError,
                creadaEn, actualizadaEn);
    }
}
