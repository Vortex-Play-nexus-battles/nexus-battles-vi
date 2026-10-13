package com.nexusbattles.ms_chatbot.chat.soporte;

import java.util.Map;
import java.util.Set;

// ms-chatbot.yaml 1.3.0 (EstadoDeTicket). Las transiciones validas viven aqui
// y no en el servicio para que la regla tenga un solo lugar:
//   ABIERTO    -> EN_PROCESO, RESUELTO o CERRADO
//   EN_PROCESO -> RESUELTO o CERRADO
//   RESUELTO   -> CERRADO o EN_PROCESO (reabrir)
//   CERRADO    -> ninguno
public enum EstadoTicket {
    ABIERTO,
    EN_PROCESO,
    RESUELTO,
    CERRADO;

    private static final Map<EstadoTicket, Set<EstadoTicket>> SIGUIENTES = Map.of(
        ABIERTO, Set.of(EN_PROCESO, RESUELTO, CERRADO),
        EN_PROCESO, Set.of(RESUELTO, CERRADO),
        RESUELTO, Set.of(CERRADO, EN_PROCESO),
        CERRADO, Set.of()
    );

    /** true si el ticket sigue pidiendo atencion (cuenta para "uno abierto por conversacion"). */
    public boolean abierto() {
        return this == ABIERTO || this == EN_PROCESO;
    }

    public boolean puedePasarA(EstadoTicket destino) {
        return SIGUIENTES.get(this).contains(destino);
    }
}
