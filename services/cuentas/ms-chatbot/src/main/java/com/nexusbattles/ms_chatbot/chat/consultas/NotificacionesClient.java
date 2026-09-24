package com.nexusbattles.ms_chatbot.chat.consultas;

import com.nexusbattles.ms_chatbot.chat.consultas.dto.BandejaResponseDto;

public interface NotificacionesClient {
    BandejaResponseDto consultarBandeja(String tokenBearer, String uid);
}
