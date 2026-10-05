package nexus.combate.api;

import java.net.URI;
import java.util.List;
import nexus.combate.FabricaProcesadorPerdidaEquipo;
import nexus.combate.IntegracionBotinException;
import nexus.combate.MotivoFinPartida;
import nexus.combate.ParticipantePerdidaEquipo;
import nexus.combate.ProcesadorPerdidaEquipo;
import nexus.combate.ResultadoPartida;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/combate/botin")
public class BotinPartidaController {

    private final FabricaProcesadorPerdidaEquipo fabrica;

    public BotinPartidaController(FabricaProcesadorPerdidaEquipo fabrica) {
        this.fabrica = fabrica;
    }

    @PostMapping("/cierres")
    public CierreBotinPartidaResponse cerrar(@RequestBody CierreBotinPartidaRequest peticion) {
        List<ParticipantePerdidaEquipo> participantes = peticion.participantes().stream()
                .map(participante -> new ParticipantePerdidaEquipo(
                        participante.combatienteId(),
                        participante.equipoId(),
                        participante.propietarioId(),
                        participante.heroeInventarioId()))
                .toList();
        ProcesadorPerdidaEquipo procesador = fabrica.crear(
                "botin-partida:" + peticion.partidaId(), participantes);
        procesador.procesar(new ResultadoPartida(
                peticion.equipoGanadorId(), MotivoFinPartida.SUPERVIVENCIA));
        return CierreBotinPartidaResponse.de(peticion.partidaId(), procesador.resultado());
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail peticionInvalida(Exception error) {
        return problema(HttpStatus.BAD_REQUEST, "peticion-invalida",
                "La petición de botín no es válida", error.getMessage());
    }

    @ExceptionHandler(IntegracionBotinException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ProblemDetail integracionNoDisponible(IntegracionBotinException error) {
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "botin-no-disponible",
                "El botín no se pudo transferir", error.getMessage());
    }

    private static ProblemDetail problema(HttpStatus estado, String tipo, String titulo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setType(URI.create("https://nexusbattles.local/errores/" + tipo));
        problema.setTitle(titulo);
        return problema;
    }
}
