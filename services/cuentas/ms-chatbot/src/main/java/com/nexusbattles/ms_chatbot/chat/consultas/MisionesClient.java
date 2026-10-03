package com.nexusbattles.ms_chatbot.chat.consultas;

import com.nexusbattles.ms_chatbot.chat.consultas.dto.MisionActivaDto;

import java.util.List;

// 7.4.4 «progreso en misiones activas» (ms-chatbot.yaml 1.3.0). Se consulta con
// el MISMO token del jugador: misiones deriva el dueno del claim uid
// (misiones.yaml 1.0.0). Nunca con una credencial de servicio + uid. Sin
// cache: la respuesta depende de quien pregunta.
public interface MisionesClient {

    List<MisionActivaDto> enCurso(String tokenBearer);
}
