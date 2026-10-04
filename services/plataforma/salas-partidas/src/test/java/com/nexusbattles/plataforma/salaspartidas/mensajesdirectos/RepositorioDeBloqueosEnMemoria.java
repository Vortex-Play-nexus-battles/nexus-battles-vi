package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** {@link RepositorioDeBloqueos} en memoria, con lo justo para mirar que paso. */
final class RepositorioDeBloqueosEnMemoria implements RepositorioDeBloqueos {

    /** Quien bloqueo a quien, y cuando. */
    final Map<List<UUID>, Instant> bloqueos = new LinkedHashMap<>();
    int consultas;

    @Override
    public boolean bloquear(UUID quien, UUID aQuien, Instant cuando) {
        return bloqueos.putIfAbsent(List.of(quien, aQuien), cuando) == null;
    }

    @Override
    public boolean desbloquear(UUID quien, UUID aQuien) {
        return bloqueos.remove(List.of(quien, aQuien)) != null;
    }

    @Override
    public boolean bloqueo(UUID quien, UUID aQuien) {
        consultas++;
        return bloqueos.containsKey(List.of(quien, aQuien));
    }

    @Override
    public Set<UUID> bloqueadosPor(UUID quien) {
        consultas++;
        return bloqueos.keySet().stream()
                .filter(par -> par.get(0).equals(quien))
                .map(par -> par.get(1))
                .collect(Collectors.toSet());
    }

    @Override
    public Set<UUID> quienesBloquearonA(UUID jugador) {
        consultas++;
        return bloqueos.keySet().stream()
                .filter(par -> par.get(1).equals(jugador))
                .map(par -> par.get(0))
                .collect(Collectors.toSet());
    }
}
