package nexus.combate.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import nexus.combate.ResultadoPerdidaEquipo;

public record CierreBotinPartidaResponse(
        UUID partidaId,
        List<AsignacionBotinResponse> asignaciones) {

    static CierreBotinPartidaResponse de(UUID partidaId, ResultadoPerdidaEquipo resultado) {
        return new CierreBotinPartidaResponse(
                partidaId,
                resultado.asignaciones().stream().map(AsignacionBotinResponse::de).toList());
    }

    public record AsignacionBotinResponse(
            String combatienteDerrotadoId,
            String elementoId,
            String productoId,
            String propietarioGanadorId,
            BigDecimal tasaDeCaida) {

        static AsignacionBotinResponse de(nexus.combate.PerdidaEquipoAsignada asignacion) {
            return new AsignacionBotinResponse(
                    asignacion.combatienteDerrotadoId(),
                    asignacion.elementoId(),
                    asignacion.productoId(),
                    asignacion.propietarioGanadorId(),
                    asignacion.tasaDeCaida());
        }
    }
}
