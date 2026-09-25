package com.nexusbattles.ms_chatbot.chat.consultas;

import com.nexusbattles.ms_chatbot.chat.consultas.dto.PaginaMovimientosDto;

// B11 — 7.4.4 «historial de transacciones reciente». Se consulta con el MISMO
// token del jugador: ms-finanzas solo le deja ver su propio uid (creditos.yaml
// 1.3.0). Nunca con una credencial de servicio, que veria el de cualquiera.
public interface FinanzasClient {

    PaginaMovimientosDto movimientos(String tokenBearer, String uid, int tamano);
}
