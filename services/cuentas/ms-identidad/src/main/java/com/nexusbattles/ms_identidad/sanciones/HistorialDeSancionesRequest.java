package com.nexusbattles.ms_identidad.sanciones;

import java.time.OffsetDateTime;

/**
 * {@code HistorialDeSanciones} de ms-identidad-admin.yaml 1.7.0: el resumen del
 * historial de sanciones de una cuenta que fija moderacion-sanciones.
 *
 * @param total    cuantas sanciones se le han emitido (las revertidas tambien); 0 o mas
 * @param ultimaEn cuando se emitio la mas reciente; nulo si {@code total} es 0
 */
public record HistorialDeSancionesRequest(Integer total, OffsetDateTime ultimaEn) {
}
