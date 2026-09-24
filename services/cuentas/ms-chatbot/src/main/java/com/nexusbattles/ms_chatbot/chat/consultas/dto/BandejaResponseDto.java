package com.nexusbattles.ms_chatbot.chat.consultas.dto;

import java.util.List;

public record BandejaResponseDto(
    int noLeidas,
    List<AvisoDto> avisos
) {}
