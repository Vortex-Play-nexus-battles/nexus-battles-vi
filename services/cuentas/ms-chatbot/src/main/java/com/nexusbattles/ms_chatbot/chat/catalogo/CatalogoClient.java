package com.nexusbattles.ms_chatbot.chat.catalogo;

/**
 * 1.10.0 — el catalogo publico de productos ({@code productos.yaml},
 * {@code GET /api/v1/productos}). Sin credencial: es el mismo dato que ve
 * cualquier visitante en la tienda.
 */
public interface CatalogoClient {

    /** Una pagina del catalogo (los productos que se pueden mostrar y comprar). */
    PaginaDelCatalogoDto pagina(int numero, int tamano);
}
