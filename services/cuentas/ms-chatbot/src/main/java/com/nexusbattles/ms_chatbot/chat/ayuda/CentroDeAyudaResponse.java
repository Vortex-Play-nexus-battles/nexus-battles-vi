package com.nexusbattles.ms_chatbot.chat.ayuda;

import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;

import java.util.List;

/**
 * ms-chatbot.yaml 1.8.0: {@code CentroDeAyuda}. Solo lo que lee un jugador:
 * ningún campo interno del tema (clave, variantes, prioridad, versión).
 */
public record CentroDeAyudaResponse(List<CategoriaDeAyuda> categorias) {

    /** {@code CategoriaDeAyuda}: una categoría con sus temas y su ancla en la página. */
    public record CategoriaDeAyuda(Categoria categoria, String ancla, List<TemaDeAyuda> temas) {
    }

    /** {@code TemaDeAyuda}: título, respuesta en los dos idiomas y sus pasos. */
    public record TemaDeAyuda(String titulo, String respuestaEs, String respuestaEn, List<String> pasosEs,
                              List<String> pasosEn) {
    }
}
