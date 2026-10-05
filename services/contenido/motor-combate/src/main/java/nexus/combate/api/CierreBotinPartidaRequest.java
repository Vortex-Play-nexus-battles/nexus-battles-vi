package nexus.combate.api;

import java.util.List;
import java.util.UUID;

public record CierreBotinPartidaRequest(
        UUID partidaId,
        String equipoGanadorId,
        List<ParticipanteBotinRequest> participantes) {

    public CierreBotinPartidaRequest {
        if (partidaId == null) {
            throw new IllegalArgumentException("partidaId es obligatorio");
        }
        exigirTexto(equipoGanadorId, "equipoGanadorId");
        if (participantes == null || participantes.size() < 2 || participantes.size() > 6) {
            throw new IllegalArgumentException("La partida requiere entre dos y seis participantes");
        }
        if (participantes.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Un participante no puede ser nulo");
        }
        participantes = List.copyOf(participantes);
    }

    public record ParticipanteBotinRequest(
            String combatienteId,
            String equipoId,
            String propietarioId,
            String heroeInventarioId) {

        public ParticipanteBotinRequest {
            exigirTexto(combatienteId, "combatienteId");
            exigirTexto(equipoId, "equipoId");
            exigirTexto(propietarioId, "propietarioId");
            exigirTexto(heroeInventarioId, "heroeInventarioId");
        }
    }

    private static void exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " es obligatorio");
        }
    }
}
