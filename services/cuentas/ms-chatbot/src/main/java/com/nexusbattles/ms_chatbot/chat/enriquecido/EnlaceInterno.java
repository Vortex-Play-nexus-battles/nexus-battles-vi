package com.nexusbattles.ms_chatbot.chat.enriquecido;

import com.fasterxml.jackson.annotation.JsonInclude;

// ms-chatbot.yaml 1.3.4: EnlaceInterno. `destino` es el id de una vista de la
// matriz de acceso del frontend (comun/matriz-acceso.js), nunca una URL: el
// chatbot no puede mandar a nadie fuera del sitio.
//
// 1.8.0: `ancla`, el fragmento dentro de esa vista (p. ej. la categoria en el
// centro de ayuda). Sin ancla no se escribe, asi que un enlace de siempre se
// guarda y se manda exactamente como antes.
public record EnlaceInterno(String texto, String destino,
                            @JsonInclude(JsonInclude.Include.NON_NULL) String ancla) {

    public EnlaceInterno(String texto, String destino) {
        this(texto, destino, null);
    }
}
