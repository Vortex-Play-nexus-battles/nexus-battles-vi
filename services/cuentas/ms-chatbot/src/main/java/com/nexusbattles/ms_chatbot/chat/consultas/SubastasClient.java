package com.nexusbattles.ms_chatbot.chat.consultas;

import com.nexusbattles.ms_chatbot.chat.consultas.dto.MiResumenDto;

public interface SubastasClient {
    MiResumenDto consultarMiResumen(String tokenBearer);
}
