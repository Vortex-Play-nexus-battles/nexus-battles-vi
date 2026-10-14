package com.nexusbattles.ms_chatbot.chat.consultas;

import com.nexusbattles.ms_chatbot.chat.consultas.dto.PaginaInventarioDto;

public interface InventarioClient {
    PaginaInventarioDto consultarInventario(String tokenBearer, int pagina);

    // 1.5.0 (7.4.4 «buscar productos en el inventario»): la busqueda real del
    // inventario (inventario.yaml, GET /elementos/busqueda; al menos 4
    // caracteres), con el token del propio jugador.
    PaginaInventarioDto buscar(String tokenBearer, String criterio);
}
