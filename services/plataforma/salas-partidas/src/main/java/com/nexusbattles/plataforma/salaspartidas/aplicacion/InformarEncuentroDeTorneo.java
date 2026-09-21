package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoPartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParticipanteDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeVinculosDeTorneo;
import com.nexusbattles.plataforma.salaspartidas.dominio.VinculoDeTorneo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * HU-TOR-004 (CA-04): al terminar una partida que es un encuentro de torneo,
 * el ganador se informa a torneos por API. Si torneos no responde, queda
 * anotado en el vinculo con el motivo (nunca en silencio) y un administrador
 * puede registrarlo con motivo (RF-ADM-005) o reintentarse mas tarde.
 *
 * <p>El ganador que viaja es un jugador humano en pie ({@code ganadorUid});
 * torneos resuelve a que equipo pertenece. Si gana la maquina no hay uid que
 * mandar: se anota y lo resuelve el administrador (la maquina no tiene cuenta).
 */
public class InformarEncuentroDeTorneo {

    private static final Logger BITACORA = LoggerFactory.getLogger(InformarEncuentroDeTorneo.class);

    private final RepositorioDeVinculosDeTorneo vinculos;
    private final ArbitroDeTorneo arbitro;
    private final Clock reloj;

    public InformarEncuentroDeTorneo(RepositorioDeVinculosDeTorneo vinculos, ArbitroDeTorneo arbitro, Clock reloj) {
        this.vinculos = Objects.requireNonNull(vinculos);
        this.arbitro = Objects.requireNonNull(arbitro);
        this.reloj = Objects.requireNonNull(reloj);
    }

    /** Vincula la sala al encuentro. Idempotente por sala: una sala es un solo encuentro. */
    public VinculoDeTorneo vincular(UUID idSala, UUID idTorneo, int numero, UUID quien) {
        VinculoDeTorneo vinculo = VinculoDeTorneo.nuevo(idSala, idTorneo, numero, quien, reloj.instant());
        vinculos.guardar(vinculo);
        BITACORA.info("Sala {} vinculada al encuentro {} del torneo {} por {}", idSala, numero, idTorneo, quien);
        return vinculo;
    }

    public Optional<VinculoDeTorneo> vinculoDe(UUID idSala) {
        return vinculos.buscarPorSala(idSala);
    }

    /**
     * @return el vinculo actualizado si la partida era un encuentro; vacio si no lo era
     */
    public Optional<VinculoDeTorneo> alTerminar(Partida partida) {
        Objects.requireNonNull(partida);
        if (partida.estado() != EstadoPartida.FINALIZADA) {
            return Optional.empty();
        }
        Optional<VinculoDeTorneo> encontrado = vinculos.buscarPorSala(partida.idSala());
        if (encontrado.isEmpty() || encontrado.get().informado()) {
            return encontrado;
        }
        VinculoDeTorneo vinculo = encontrado.get();
        Optional<UUID> ganadorHumano = partida.ganadores().stream()
                .filter(p -> !p.esIA())
                .map(ParticipanteDePartida::idJugador)
                .findFirst();
        VinculoDeTorneo resultado;
        if (ganadorHumano.isEmpty()) {
            resultado = vinculo.fallo("gano la maquina o hubo empate: el administrador registra el resultado con motivo");
            BITACORA.warn("Encuentro {} del torneo {}: sin ganador humano en la partida {}; queda para el administrador",
                    vinculo.numero(), vinculo.idTorneo(), partida.id());
        } else {
            try {
                arbitro.informarGanador(vinculo.idTorneo(), vinculo.numero(), ganadorHumano.get(), partida.id());
                resultado = vinculo.informado(reloj.instant());
                BITACORA.info("Encuentro {} del torneo {} informado: gana {} (partida {})", vinculo.numero(),
                        vinculo.idTorneo(), ganadorHumano.get(), partida.id());
            } catch (ArbitroDeTorneo.TorneoNoDisponible fallo) {
                resultado = vinculo.fallo(fallo.getMessage());
                BITACORA.warn("Encuentro {} del torneo {}: torneos no acepto el resultado ({}); queda anotado",
                        vinculo.numero(), vinculo.idTorneo(), fallo.getMessage());
            }
        }
        vinculos.guardar(resultado);
        return Optional.of(resultado);
    }
}
