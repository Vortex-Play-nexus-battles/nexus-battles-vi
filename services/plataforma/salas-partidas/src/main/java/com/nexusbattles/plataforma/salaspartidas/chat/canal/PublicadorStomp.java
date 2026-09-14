package com.nexusbattles.plataforma.salaspartidas.chat.canal;

import com.nexusbattles.plataforma.salaspartidas.chat.MensajeDeChat;
import com.nexusbattles.plataforma.salaspartidas.chat.PublicadorDeChat;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Publica al destino del canal; todas las sesiones suscritas lo reciben. */
@Component
class PublicadorStomp implements PublicadorDeChat {

    private final SimpMessagingTemplate plantilla;

    PublicadorStomp(SimpMessagingTemplate plantilla) {
        this.plantilla = plantilla;
    }

    @Override
    public void publicar(MensajeDeChat mensaje) {
        plantilla.convertAndSend(mensaje.canal().destino(), MensajeDeChatResponse.de(mensaje));
    }
}
