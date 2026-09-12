package com.nexusbattles.ms_subastas.subastas.dto;

import java.util.List;

/**
 * HU-SUB-011. Respuesta del autocompletado -- corresponde al schema
 * SugerenciasResponse de contracts/openapi/ms-subastas-listado.yaml.
 * Deliberadamente mas liviano que PaginaDeSubastasResponse: solo nombres,
 * sin paginacion ni el resto de los campos del listado completo.
 */
public record SugerenciasResponse(List<String> sugerencias) {
}
