package com.nexusbattles.ms_chatbot.chat.consultas.dto;

import java.util.List;

public record PaginaInventarioDto(
    List<ElementoInventarioDto> elementos,
    int totalElementos
) {}
