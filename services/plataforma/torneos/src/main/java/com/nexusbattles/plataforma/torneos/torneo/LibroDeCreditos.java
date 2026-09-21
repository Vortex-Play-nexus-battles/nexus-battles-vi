package com.nexusbattles.plataforma.torneos.torneo;

import java.util.UUID;

/**
 * Puerto hacia el libro de creditos (contracts/openapi/creditos.yaml): la
 * inscripcion se reserva al inscribirse, se cobra al iniciar el torneo y se
 * devuelve si se cancela (RF-TOR-002, CA-04).
 */
public interface LibroDeCreditos {

    /**
     * @return identificador de la reserva
     * @throws TorneoRechazado CREDITOS_INSUFICIENTES o LIBRO_NO_DISPONIBLE
     */
    UUID reservar(UUID jugador, int creditos, String claveIdempotente, String referencia);

    /** @throws TorneoRechazado LIBRO_NO_DISPONIBLE */
    void liberar(UUID reservaId);

    /** @throws TorneoRechazado LIBRO_NO_DISPONIBLE */
    void consumir(UUID reservaId);
}
