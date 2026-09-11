package com.nexusbattles.ms_subastas.pujas.service;

import com.nexusbattles.ms_subastas.pujas.model.PujaAutomatica;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Calcula si una puja automatica debe reaccionar a una nueva oferta vigente,
 * y con que monto. No emite la puja ni respeta por si mismo el intervalo de
 * 5 s: eso lo hace MotorPujasService.pujar cuando el caller (un listener de
 * PujaRealizada, aun sin construir) invoque con el ContextoParticipacion
 * correcto. Separado para poder testear la decision "cuanto ofrecer / cuando
 * rendirse" sin reservar creditos de verdad.
 */
@Service
public class MotorPujaAutomaticaService {

    public Optional<BigDecimal> calcularRespuesta(Subasta subasta, PujaAutomatica automatico) {
        if (!automatico.isActiva()) {
            return Optional.empty();
        }
        if (subasta.getMejorPostorId() != null && subasta.getMejorPostorId().equals(automatico.getJugadorId())) {
            return Optional.empty();
        }

        BigDecimal siguienteOferta = subasta.getOfertaVigente().add(subasta.getIncrementoMinimo());
        if (siguienteOferta.compareTo(automatico.getLimite()) > 0) {
            automatico.setActiva(false);
            return Optional.empty();
        }

        return Optional.of(siguienteOferta);
    }
}
