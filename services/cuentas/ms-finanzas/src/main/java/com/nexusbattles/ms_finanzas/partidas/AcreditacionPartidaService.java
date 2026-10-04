package com.nexusbattles.ms_finanzas.partidas;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
 *   <li>Llama a {@link CofreService} con los créditos de los GANADORES —
 *       §7.6: «veinte (20) créditos en juegos ganados»; el crédito de
 *       participar no cuenta (cofres.yaml 1.1.0, B7)— para que acumulen hacia
 *       su cofre y lo reciban si completan la cuota.</li>
 *   <li>Los cofres ganados se entregan al inventario DESPUÉS de confirmar la
 *       transacción ({@link EntregaDeCofres}), nunca dentro.</li>
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
    private final EntregaDeCofres entregaDeCofres;
    private final Clock reloj;

    public AcreditacionPartidaService(
            PartidaProcesadaRepository partidaProcesadaRepositorio,
            CreditoService creditoService,
            CofreService cofreService,
            EntregaDeCofres entregaDeCofres,
            Clock reloj) {
        this.partidaProcesadaRepositorio = partidaProcesadaRepositorio;
        this.creditoService = creditoService;
        this.cofreService = cofreService;
        this.entregaDeCofres = entregaDeCofres;
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
        List<UUID> cofresGanados = new ArrayList<>();
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

            // Solo los créditos de una partida GANADA cuentan para el cofre
            // (§7.6); el de participar no (cofres.yaml 1.1.0).
            Optional<CofreEntregado> cofre = esGanador
                    ? cofreService.registrarCreditosGanados(participante.uid(), monto)
                    : Optional.empty();
            cofre.map(CofreEntregado::getId).ifPresent(cofresGanados::add);

            acreditaciones.add(new AcreditacionAplicada(
                    participante.uid(), monto, esGanador,
                    cofre.map(CofreEntregado::getId).orElse(null)));
        }

        PartidaProcesada marca = new PartidaProcesada();
        marca.setPartidaId(req.partidaId());
        marca.setProcesadoEn(Instant.now(reloj));
        partidaProcesadaRepositorio.save(marca);

        entregaDeCofres.entregarTrasConfirmar(cofresGanados);
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
