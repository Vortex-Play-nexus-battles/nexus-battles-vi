package com.nexusbattles.ms_subastas.subastas.port;

import com.nexusbattles.ms_subastas.subastas.dto.PublicarSubastaResponse;
import java.util.Optional;
import java.util.UUID;

public interface IdempotenciaPublicacion {
    Optional<Resultado> buscar(String clave);
    /** Adquiere antes de efectos externos; un duplicado en curso se rechaza con conflicto. */
    Adquisicion adquirir(String clave, String huella);
    void confirmar(String clave, UUID titular, PublicarSubastaResponse respuesta);
    void liberar(String clave, UUID titular);
    void marcarIncierta(String clave, UUID titular);
    record Adquisicion(UUID titular, Optional<Resultado> resultado) { }
    record Resultado(String huella, UUID subastaId, PublicarSubastaResponse respuesta) { }
}
