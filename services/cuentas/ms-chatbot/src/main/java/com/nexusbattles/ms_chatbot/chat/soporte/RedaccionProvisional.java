package com.nexusbattles.ms_chatbot.chat.soporte;

import org.springframework.stereotype.Component;

// PROVISIONAL, solo mientras llega la redaccion de plataforma (ver
// RedaccionDeDatosSensibles). Devuelve el texto tal cual. Se borra en el
// mismo commit que conecta el componente de plataforma, y el PR de los tickets
// no se abre mientras esta clase exista.
@Component
public class RedaccionProvisional implements RedaccionDeDatosSensibles {

    @Override
    public String redactar(String texto) {
        return texto;
    }
}
