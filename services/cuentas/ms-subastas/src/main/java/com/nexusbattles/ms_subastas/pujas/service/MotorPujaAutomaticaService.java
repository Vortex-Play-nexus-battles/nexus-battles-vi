package com.nexusbattles.ms_subastas.pujas.service;

import com.nexusbattles.ms_subastas.pujas.model.PujaAutomatica;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puja automatica de HU-SUB-004: decide cuanto ofrecer, a quien le toca
 * responder cuando hay varias configuradas sobre la misma subasta, y cuando
 * puede volver a emitir sin violar el intervalo minimo.
 *
 * No emite la puja ni reserva creditos: eso lo hace MotorPujasService cuando
 * el caller (un listener de PujaRealizada, aun sin construir) aplique la
 * decision. Separado asi para poder probar la decision sin tocar creditos.
 *
 * ASUNCION PENDIENTE DE VALIDAR CON EL CLIENTE: cuando dos jugadores tienen
 * puja automatica en la misma subasta, se resuelve de forma ITERATIVA — cada
 * uno ofrece la oferta vigente mas el incremento minimo, por turnos,
 * respetando los 5 s, hasta que uno alcanza su limite. La alternativa
 * (saltar de una al limite del segundo mayor, estilo eBay) converge en un
 * paso pero no es lo que dice el criterio de aceptacion, que habla de
 * "emitir ofertas respetando el intervalo minimo". Con el intervalo de 5 s el
 * modo iterativo puede tardar minutos en converger, asi que conviene
 * confirmarlo antes de la demo.
 */
@Service
@RequiredArgsConstructor
public class MotorPujaAutomaticaService {

    private final Clock clock;
    private final ParametrosPuja parametros;

    /**
     * Valida y arma la configuracion de una puja automatica. El saldo lo
     * consulta el caller a ms-finanzas y se pasa aqui para no acoplar esta
     * decision a una llamada remota.
     *
     * Se valida el saldo CONTRA EL LIMITE al configurar, no al emitir: el
     * criterio de aceptacion no lo especifica, pero avisar al jugador en el
     * momento de configurar es preferible a que su puja automatica falle en
     * silencio mas tarde. Otra asuncion a confirmar con el cliente.
     */
    public PujaAutomatica configurar(Subasta subasta, UUID jugadorId, BigDecimal limite, BigDecimal saldoDisponible) {
        if (!subasta.estaActiva()) {
            throw new PujaRechazadaException(PujaRechazadaException.Motivo.SUBASTA_NO_ACTIVA,
                    "La subasta " + subasta.getId() + " no esta activa");
        }
        if (subasta.esVendedor(jugadorId)) {
            throw new PujaRechazadaException(PujaRechazadaException.Motivo.PUJA_PROPIA,
                    "El jugador " + jugadorId + " no puede configurar una puja automatica en su propia subasta");
        }

        BigDecimal ofertaMinimaValida = subasta.getOfertaVigente().add(subasta.getIncrementoMinimo());
        if (limite.compareTo(ofertaMinimaValida) < 0) {
            throw new PujaRechazadaException(PujaRechazadaException.Motivo.LIMITE_AUTOMATICO_INALCANZABLE,
                    "Un limite de " + limite + " nunca podria pujar: la siguiente oferta valida es " + ofertaMinimaValida);
        }
        if (saldoDisponible.compareTo(limite) < 0) {
            throw new PujaRechazadaException(PujaRechazadaException.Motivo.SALDO_INSUFICIENTE_PARA_LIMITE,
                    "El saldo disponible (" + saldoDisponible + ") no cubre el limite configurado (" + limite + ")");
        }

        return new PujaAutomatica(UUID.randomUUID(), subasta.getId(), jugadorId, limite, true);
    }

    /**
     * De todas las pujas automaticas de la subasta, elige la que debe
     * responder: la de mayor limite que todavia alcanza la siguiente oferta
     * valida. Las que ya no alcanzan quedan desactivadas, para que el caller
     * notifique a su dueno que llego al limite.
     */
    public Optional<PujaAutomatica> elegirSiguiente(Subasta subasta, List<PujaAutomatica> candidatas) {
        List<PujaAutomatica> vigentes = candidatas.stream()
                .filter(PujaAutomatica::isActiva)
                .filter(automatico -> !esElMejorPostor(subasta, automatico))
                .toList();

        vigentes.stream()
                .filter(automatico -> !alcanzaLaSiguienteOferta(subasta, automatico))
                .forEach(automatico -> automatico.setActiva(false));

        return vigentes.stream()
                .filter(PujaAutomatica::isActiva)
                .max(Comparator.comparing(PujaAutomatica::getLimite));
    }

    public Optional<BigDecimal> calcularRespuesta(Subasta subasta, PujaAutomatica automatico) {
        if (!automatico.isActiva() || esElMejorPostor(subasta, automatico)) {
            return Optional.empty();
        }
        if (!alcanzaLaSiguienteOferta(subasta, automatico)) {
            automatico.setActiva(false);
            return Optional.empty();
        }
        return Optional.of(siguienteOferta(subasta));
    }

    /**
     * Primer instante en que este jugador puede volver a pujar sin violar el
     * intervalo minimo. El planificador que emita las pujas automaticas debe
     * reencolar hasta este instante en vez de dormir un hilo.
     */
    public Instant disponibleDesde(Instant ultimaPujaDelJugador) {
        if (ultimaPujaDelJugador == null) {
            return clock.instant();
        }
        return ultimaPujaDelJugador.plusSeconds(parametros.getIntervaloMinimoSegundos());
    }

    private BigDecimal siguienteOferta(Subasta subasta) {
        return subasta.getOfertaVigente().add(subasta.getIncrementoMinimo());
    }

    private boolean alcanzaLaSiguienteOferta(Subasta subasta, PujaAutomatica automatico) {
        return siguienteOferta(subasta).compareTo(automatico.getLimite()) <= 0;
    }

    private boolean esElMejorPostor(Subasta subasta, PujaAutomatica automatico) {
        return subasta.getMejorPostorId() != null && subasta.getMejorPostorId().equals(automatico.getJugadorId());
    }
}
