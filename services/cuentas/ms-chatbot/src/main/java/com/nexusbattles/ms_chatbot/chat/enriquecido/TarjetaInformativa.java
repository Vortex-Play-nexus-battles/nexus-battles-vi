package com.nexusbattles.ms_chatbot.chat.enriquecido;

import com.fasterxml.jackson.annotation.JsonInclude;

// ms-chatbot.yaml 1.3.4: TarjetaInformativa.
//
// 1.13.0 (§7.4.3 «respuestas enriquecidas: incluyendo imagenes»): `imagen`, la
// del producto de la tarjeta, ya filtrada por ImagenDeProducto (un recurso
// grafico del catalogo o una imagen incrustada pequena; nunca una URL
// externa). Sin imagen no se escribe, asi que una tarjeta de siempre se
// guarda y se manda exactamente como antes.
public record TarjetaInformativa(String titulo, String texto, EnlaceInterno enlace,
                                 @JsonInclude(JsonInclude.Include.NON_NULL) String imagen) {

    public TarjetaInformativa(String titulo, String texto, EnlaceInterno enlace) {
        this(titulo, texto, enlace, null);
    }
}
