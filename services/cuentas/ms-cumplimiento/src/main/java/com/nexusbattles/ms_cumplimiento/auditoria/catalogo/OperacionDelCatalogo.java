package com.nexusbattles.ms_cumplimiento.auditoria.catalogo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * Una operación administrativa del catálogo tal como la entrega productos
 * (productos.yaml 1.12.0, esquema {@code OperacionAdministrativa}). Los tipos
 * llegan como texto: uno que esta bitácora todavía no conoce se registra
 * igual, como {@code OTRO}, en vez de perderse.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OperacionDelCatalogo(
    String id,
    Instant ocurridaEn,
    String autor,
    String tipo,
    String objeto,
    String objetoId,
    String nombre,
    String valorAnterior,
    String valorNuevo,
    String motivo,
    String descripcion,
    String ipOrigen) {
}
