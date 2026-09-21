package com.nexusbattles.ms_finanzas.partidas;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Marca una partida como ya procesada por {@link AcreditacionPartidaService}.
 * Es el registro que da idempotencia al endpoint {@code POST /partidas/resultado}:
 * si viene un reintento con el mismo {@code partidaId} (típico por timeout de
 * red desde ms-salas-partidas), no se vuelven a acreditar créditos ni a
 * entregar cofres.
 */
@Entity
@Table(name = "partida_procesada")
@Getter
@Setter
@NoArgsConstructor
public class PartidaProcesada {

    @Id
    @Column(name = "partida_id", length = 128, nullable = false)
    private String partidaId;

    @Column(name = "procesado_en", nullable = false, updatable = false)
    private Instant procesadoEn;
}
