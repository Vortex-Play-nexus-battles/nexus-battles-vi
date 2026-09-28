package com.nexusbattles.ms_chatbot.chat.moderacion;

// El mensaje contiene un termino de la lista negra: no se procesa ni se guarda
// (422, motivo CONTENIDO_BLOQUEADO). El detalle no dice que termino fue, igual
// que la propia lista negra sin token de moderacion.
public class ContenidoBloqueado extends RuntimeException {

    public ContenidoBloqueado() {
        super("Tu mensaje contiene términos que no están permitidos. Reformúlalo, por favor.");
    }
}
