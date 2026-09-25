package com.nexusbattles.plataforma.torneos.torneo;

import java.util.UUID;

/**
 * Puerto hacia el libro de creditos (contracts/openapi/creditos.yaml): la
 * inscripcion se reserva al inscribirse, se cobra al iniciar el torneo y se
 * devuelve si se cancela (RF-TOR-002, CA-04); el premio se acredita al
 * terminar (RF-TOR-007).
 *
 * <p>Reservar es la unica llamada que se hace dentro de una peticion del
 * jugador y por eso responde con {@link TorneoRechazado} (el jugador tiene
 * que saber si le alcanzo). Las demas las ejecuta el procesador de
 * operaciones fuera de toda transaccion y fallan con
 * {@link FalloDeIntegracion}, que dice si hay que reintentar.
 */
public interface LibroDeCreditos {

    /** Reserva devuelta por el libro; puede venir ya cerrada si la clave se reutilizo. */
    record Reserva(UUID id, String estado) {
        public boolean activa() {
            return "ACTIVA".equals(estado);
        }
    }

    /**
     * @return la reserva (la ya existente si la clave se repite)
     * @throws TorneoRechazado CREDITOS_INSUFICIENTES o LIBRO_NO_DISPONIBLE
     */
    Reserva reservar(UUID jugador, int creditos, String claveIdempotente, String referencia);

    /**
     * Libera la reserva; idempotente en el libro.
     *
     * @return el estado en que quedo: {@code LIBERADA}, o {@code CONSUMIDA} si
     *         ya se habia cobrado (entonces la devolucion tiene que acreditar)
     * @throws FalloDeIntegracion si no se pudo
     */
    String liberar(UUID reservaId);

    /**
     * Cobra la reserva; idempotente en el libro (una ya consumida responde 200).
     *
     * @throws FalloDeIntegracion si no se pudo; no reintentable si la reserva
     *                            ya estaba liberada o no existe
     */
    void consumir(UUID reservaId);

    /**
     * Suma creditos al jugador; idempotente por {@code refId}.
     *
     * @throws FalloDeIntegracion si no se pudo
     */
    void acreditar(UUID jugador, int creditos, String refId, String concepto);
}
