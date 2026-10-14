package com.nexusbattles.plataforma.notificaciones.politicas;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Un cambio publicado en una politica de uso tal como lo entrega
 * admin-parametros — esquema {@code CambioDePolitica} de admin-parametros.yaml
 * (1.1.0). Copia propia del dato (regla 7): la {@code politica} va como texto
 * para que una politica nueva no rompa la lectura.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CambioDePoliticaDeUso(
        String id,
        String politica,
        String nombreDeLaPolitica,
        String titulo,
        String descripcion,
        Instant vigenteDesde,
        Instant publicadoEn) {
}
