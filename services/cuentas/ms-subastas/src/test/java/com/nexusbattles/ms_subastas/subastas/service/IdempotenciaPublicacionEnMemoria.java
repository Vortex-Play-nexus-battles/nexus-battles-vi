package com.nexusbattles.ms_subastas.subastas.service;

import com.nexusbattles.ms_subastas.subastas.dto.PublicarSubastaResponse;
import com.nexusbattles.ms_subastas.subastas.port.IdempotenciaPublicacion;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import static com.nexusbattles.ms_subastas.subastas.service.PublicacionSubastaException.Motivo.*;

@Component
public class IdempotenciaPublicacionEnMemoria implements IdempotenciaPublicacion {
    private enum Estado { EN_CURSO, CONFIRMADA, INCIERTA }
    private record Entrada(UUID titular, String huella, Estado estado, Resultado resultado) { }
    private final ConcurrentHashMap<String, Entrada> entradas = new ConcurrentHashMap<>();

    @Override
    public Adquisicion adquirir(String clave, String huella) {
        var nueva = new Entrada(UUID.randomUUID(), huella, Estado.EN_CURSO, null);
        var previa = entradas.putIfAbsent(clave, nueva);
        if (previa == null) return new Adquisicion(nueva.titular(), Optional.empty());
        if (!previa.huella().equals(huella)) {
            throw new PublicacionSubastaException(CONFLICTO, "La clave de idempotencia fue usada con otra solicitud");
        }
        if (previa.estado() == Estado.CONFIRMADA) return new Adquisicion(previa.titular(), Optional.of(previa.resultado()));
        if (previa.estado() == Estado.INCIERTA) {
            throw new PublicacionSubastaException(DEPENDENCIA_NO_DISPONIBLE, "Resultado transaccional desconocido; requiere conciliacion");
        }
        throw new PublicacionSubastaException(CONFLICTO, "Publicacion con esta clave en curso; reintente tras su finalizacion");
    }

    @Override
    public Optional<Resultado> buscar(String clave) {
        var entrada = entradas.get(clave);
        return entrada == null ? Optional.empty() : Optional.ofNullable(entrada.resultado());
    }

    @Override
    public void confirmar(String clave, UUID titular, PublicarSubastaResponse respuesta) {
        entradas.computeIfPresent(clave, (k, actual) -> actual.titular().equals(titular) && actual.estado() == Estado.EN_CURSO
                ? new Entrada(titular, actual.huella(), Estado.CONFIRMADA,
                        new Resultado(actual.huella(), respuesta.id(), respuesta)) : actual);
    }

    @Override
    public void liberar(String clave, UUID titular) {
        entradas.computeIfPresent(clave, (k, actual) -> actual.titular().equals(titular) && actual.estado() == Estado.EN_CURSO ? null : actual);
    }

    @Override
    public void marcarIncierta(String clave, UUID titular) {
        entradas.computeIfPresent(clave, (k, actual) -> actual.titular().equals(titular) && actual.estado() == Estado.EN_CURSO
                ? new Entrada(titular, actual.huella(), Estado.INCIERTA, null) : actual);
    }
}
