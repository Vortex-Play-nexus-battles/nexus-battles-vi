package com.nexusbattles.ms_finanzas.partidas;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexusbattles.ms_finanzas.creditos.dto.CreditoDTOs.AcreditarRequest;
import com.nexusbattles.ms_finanzas.creditos.service.CreditoService;
import com.nexusbattles.ms_finanzas.partidas.ResultadoPartidaRequest.ParticipantePartidaRequest;
import com.nexusbattles.ms_finanzas.partidas.ResultadoPartidaResponse.AcreditacionAplicada;

/**
 * Orquesta la acreditación de créditos al finalizar una partida (HU-JUE-012).
 *
 * <ol>
 *   <li>Idempotencia por {@code partidaId}: si la partida ya fue procesada
 *       (por ejemplo, ms-salas-partidas reintentó por timeout), no se
 *       vuelven a acreditar créditos ni a entregar cofres.</li>
 *   <li>Excluye a los participantes marcados como sancionados — la señal
 *       de sanción la trae la propia petición desde ms-salas-partidas o el
 *       módulo de moderación; ms-finanzas no consulta la lista negra por su
 *       cuenta (regla 7 de plataforma).</li>
 *   <li>Calcula el monto por jugador:
 *     <ul>
 *       <li>Ganador de partida 1v1 → 2 créditos</li>
 *       <li>Ganador de partida grupal → 4 créditos</li>
 *       <li>Participante no ganador (no sancionado) → 1 crédito</li>
 *     </ul>
 *   </li>
 *   <li>Delega la acreditación a {@link CreditoService#acreditar}, que ya
 *       resuelve la idempotencia por {@code refId} desde el lado financiero
 *       (por si por alguna razón la fila {@code partida_procesada} se
 *       creara pero fallara la acreditación después — ese refId nunca
 *       aplicaría dos veces).</li>
 *   <li>Llama a {@link CofreService} para que el jugador acumule los
 *       créditos ganados de la semana y reciba el cofre si corresponde.</li>
 * </ol>
 *
 * <p>Nota: {@code CreditoService} vive en el mismo módulo Java, así que se
 * consume como bean local — no hace falta ir por REST a este mismo
 * servicio.
 */
@Service
public class AcreditacionPartidaService {

    private static final int CREDITOS_GANADOR_UNO_A_UNO = 2;
    private static final int CREDITOS_GANADOR_GRUPAL = 4;
    private static final int CREDITOS_PARTICIPANTE = 1;
    private static final String CONCEPTO_GANADOR = "recompensa-victoria";
    private static final String CONCEPTO_PARTICIPANTE = "recompensa-participacion";

    private final PartidaProcesadaRepository partidaProcesadaRepositorio;
    private final CreditoService creditoService;
    private final CofreService cofreService;
    private final Clock reloj;

    public AcreditacionPartidaService(
            PartidaProcesadaRepository partidaProcesadaRepositorio,
            CreditoService creditoService,
            CofreService cofreService,
            Clock reloj) {
        this.partidaProcesadaRepositorio = partidaProcesadaRepositorio;
        this.creditoService = creditoService;
        this.cofreService = cofreService;
        this.reloj = reloj;
    }

    @Transactional
    public ResultadoPartidaResponse procesarResultadoPartida(ResultadoPartidaRequest req) {
        req.validar();
        if (partidaProcesadaRepositorio.existsById(req.partidaId())) {
            throw new PartidaYaProcesadaException(req.partidaId());
        }

        List<AcreditacionAplicada> acreditaciones = new ArrayList<>();
        List<String> sancionadosExcluidos = new ArrayList<>();
        List<String> ganadores = req.ganadores();

        for (ParticipantePartidaRequest participante : req.participantes()) {
            if (participante.sancionado()) {
                sancionadosExcluidos.add(participante.uid());
                continue;
            }

            boolean esGanador = ganadores.contains(participante.uid());
            int monto = calcularMonto(esGanador, req.tipoPartida());
            String concepto = esGanador ? CONCEPTO_GANADOR : CONCEPTO_PARTICIPANTE;
            String refId = "partida-" + req.partidaId() + "-jugador-" + participante.uid();

            creditoService.acreditar(new AcreditarRequest(
                    participante.uid(), BigDecimal.valueOf(monto), refId, concepto));

            Optional<CofreEntregado> cofre = cofreService.registrarCreditosGanados(
                    participante.uid(), monto);

            acreditaciones.add(new AcreditacionAplicada(
                    participante.uid(), monto, esGanador,
                    cofre.map(CofreEntregado::getId).orElse(null)));
        }

        PartidaProcesada marca = new PartidaProcesada();
        marca.setPartidaId(req.partidaId());
        marca.setProcesadoEn(Instant.now(reloj));
        partidaProcesadaRepositorio.save(marca);

        return new ResultadoPartidaResponse(req.partidaId(), acreditaciones, sancionadosExcluidos);
    }

    private int calcularMonto(boolean esGanador, TipoPartida tipoPartida) {
        if (!esGanador) {
            return CREDITOS_PARTICIPANTE;
        }
        return tipoPartida == TipoPartida.GRUPAL
                ? CREDITOS_GANADOR_GRUPAL
                : CREDITOS_GANADOR_UNO_A_UNO;
    }
}
