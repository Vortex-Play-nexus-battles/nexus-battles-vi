package com.nexusbattles.ms_chatbot.chat.consultas;

import com.nexusbattles.ms_chatbot.chat.consultas.dto.PaginaInventarioDto;

public interface InventarioClient {
    PaginaInventarioDto consultarInventario(String tokenBearer, int pagina);
}
