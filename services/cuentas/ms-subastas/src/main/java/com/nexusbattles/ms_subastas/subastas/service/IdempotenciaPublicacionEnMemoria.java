package com.nexusbattles.ms_subastas.subastas.service;

import com.nexusbattles.ms_subastas.subastas.dto.PublicarSubastaResponse;
import com.nexusbattles.ms_subastas.subastas.port.IdempotenciaPublicacion;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class IdempotenciaPublicacionEnMemoria implements IdempotenciaPublicacion {
    private final ConcurrentHashMap<String, Resultado> resultados = new ConcurrentHashMap<>();
    public Optional<Resultado> buscar(String clave) { return Optional.ofNullable(resultados.get(clave)); }
    public void guardar(String clave, String huella, UUID subastaId, PublicarSubastaResponse respuesta) {
        resultados.putIfAbsent(clave, new Resultado(huella, subastaId, respuesta));
    }
}
