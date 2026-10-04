package com.nexusbattles.ms_chatbot.chat.consultas;

import com.nexusbattles.ms_chatbot.chat.consultas.dto.TorneoDetalleDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.TorneoResumenDto;

import java.util.List;
import java.util.UUID;

// B11 — 7.4.4 «informacion de torneos en curso». Datos publicos de torneos:
// no se manda ninguna credencial (ni la del jugador ni una de servicio).
public interface TorneosClient {

    List<TorneoResumenDto> listar();

    TorneoDetalleDto obtener(UUID torneoId);
}
