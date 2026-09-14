package com.nexusbattles.ms_subastas.subastas.port;

import com.nexusbattles.ms_subastas.subastas.dto.PublicarSubastaResponse;
import java.util.Optional;
import java.util.UUID;

public interface IdempotenciaPublicacion {
    Optional<Resultado> buscar(String clave);
    void guardar(String clave, String huella, UUID subastaId, PublicarSubastaResponse respuesta);
    record Resultado(String huella, UUID subastaId, PublicarSubastaResponse respuesta) { }
}
