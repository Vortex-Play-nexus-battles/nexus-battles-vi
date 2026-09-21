package com.nexusbattles.plataforma.salaspartidas.dominio;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * La sala es la partida del encuentro {@code numero} del torneo {@code idTorneo}
 * — HU-TOR-004, CA-04. Cuando la partida termina, el ganador se informa a
 * torneos por API; {@code informadoEn} queda cuando torneos lo acepto.
 */
public record VinculoDeTorneo(UUID idSala, UUID idTorneo, int numero, UUID vinculadoPor, Instant vinculadoEn,
                              Instant informadoEn, String ultimoFallo) {

    public VinculoDeTorneo {
        Objects.requireNonNull(idSala);
        Objects.requireNonNull(idTorneo);
        Objects.requireNonNull(vinculadoPor);
        Objects.requireNonNull(vinculadoEn);
        if (numero < 1 || numero > 14) {
            throw new ParametrosInvalidos("torneo.numeroEncuentro", "El encuentro de un torneo va del 1 al 14");
        }
    }

    public static VinculoDeTorneo nuevo(UUID idSala, UUID idTorneo, int numero, UUID vinculadoPor, Instant ahora) {
        return new VinculoDeTorneo(idSala, idTorneo, numero, vinculadoPor, ahora, null, null);
    }

    public boolean informado() {
        return informadoEn != null;
    }

    public VinculoDeTorneo informado(Instant ahora) {
        return new VinculoDeTorneo(idSala, idTorneo, numero, vinculadoPor, vinculadoEn, ahora, null);
    }

    public VinculoDeTorneo fallo(String motivo) {
        return new VinculoDeTorneo(idSala, idTorneo, numero, vinculadoPor, vinculadoEn, null,
                motivo == null ? null : motivo.substring(0, Math.min(500, motivo.length())));
    }
}
