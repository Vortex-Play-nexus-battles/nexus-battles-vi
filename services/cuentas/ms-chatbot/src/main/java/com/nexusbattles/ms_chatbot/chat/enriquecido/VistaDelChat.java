package com.nexusbattles.ms_chatbot.chat.enriquecido;

import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;

// ms-chatbot.yaml 1.3.4: la seccion del sitio donde esta el jugador al
// escribir (7.4.3 «respuestas contextuales basadas en la ubicacion»). Cada
// vista apunta a la categoria de temas que mas probablemente busca ahi.
public enum VistaDelChat {
    INICIO(null),
    INVENTARIO(Categoria.PRODUCTO),
    MISIONES(Categoria.MODALIDAD_JUEGO),
    TORNEOS(Categoria.MODALIDAD_JUEGO),
    SUBASTAS(Categoria.SUBASTA_Y_COMERCIO),
    CUENTA(Categoria.CUENTA_Y_REGISTRO);

    private final Categoria categoria;

    VistaDelChat(Categoria categoria) {
        this.categoria = categoria;
    }

    /** La categoria relacionada con esta vista; null si no hay una (INICIO). */
    public Categoria categoria() {
        return categoria;
    }
}
