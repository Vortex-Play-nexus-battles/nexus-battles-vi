package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.CanalDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.PartidaModificadaConcurrentemente;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepartoDeCreditos;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDePartidas;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import com.nexusbattles.plataforma.salaspartidas.dominio.SalaModificadaConcurrentemente;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Cierra las salas y partidas abandonadas y devuelve su apuesta — auditoria de
 * DEV del 30-sep (500 creditos apartados en una apuesta del 28 de septiembre,
 * sin sala visible ni forma de liberarlos).
 *
 * <p><b>El hueco.</b> Una apuesta solo volvia al cancelar la sala, al
 * abandonarla antes de empezar o al terminar la partida. Una sala que nadie
 * llega a empezar, o una partida que nadie termina —no hay limite de tiempo por
 * turno (D-B7-14) ni forma de rendirse—, retenia los creditos para siempre: la
 * sala EN_JUEGO ni siquiera aparece en el listado.
 *
 * <p><b>El plazo no se inventa aqui.</b> Es el vencimiento con que ms-finanzas
 * crea toda reserva de apuesta (72 h, {@code expira_en}): una sala no puede
 * vivir mas que la reserva que la respalda. Pasado ese plazo,
 * <ul>
 *   <li>una sala abierta, llena o privada se cancela con motivo
 *       {@code INACTIVIDAD} y devuelve la apuesta de cada participante (como la
 *       cancelacion del anfitrion);</li>
 *   <li>una partida en curso se da por terminada sin ganador —empate: nadie se
 *       lleva lo de nadie— y la apuesta se liquida como tal, devolviendo lo
 *       suyo a cada uno. No se acredita la recompensa por jugar: una partida
 *       abandonada no es una partida jugada.</li>
 * </ul>
 * El plazo es configurable ({@code salas.abandono.horas}) y queda anotado como
 * decision pendiente del PO (D-39). ms-finanzas libera ademas por su cuenta
 * cualquier reserva de apuesta vencida (la red de seguridad para lo que este
 * servicio no conoce: una reserva cuya respuesta se perdio por la red).
 *
 * <p>Cada sala y cada partida se cierra por separado: una que falla se anota y
 * se reintenta en la siguiente vuelta, sin detener las demas.
 */
public class CerrarAbandonadas {

    private static final Logger BITACORA = LoggerFactory.getLogger(CerrarAbandonadas.class);

    /** Cuantas salas y cuantas partidas se cierran por vuelta, como mucho. */
    static final int LOTE = 50;

    private final RepositorioDeSalas salas;
    private final RepositorioDePartidas partidas;
    private final CancelarSala cancelar;
    private final LiquidarApuesta apuesta;
    private final CanalDePartida canal;
    private final Clock reloj;
    private final Duration plazo;

    public CerrarAbandonadas(RepositorioDeSalas salas, RepositorioDePartidas partidas, CancelarSala cancelar,
                             LiquidarApuesta apuesta, CanalDePartida canal, Clock reloj, Duration plazo) {
        this.salas = Objects.requireNonNull(salas);
        this.partidas = Objects.requireNonNull(partidas);
        this.cancelar = Objects.requireNonNull(cancelar);
        this.apuesta = Objects.requireNonNull(apuesta);
        this.canal = Objects.requireNonNull(canal);
        this.reloj = Objects.requireNonNull(reloj);
        if (plazo == null || plazo.isZero() || plazo.isNegative()) {
            throw new IllegalArgumentException("El plazo de abandono tiene que durar algo.");
        }
        this.plazo = plazo;
    }

    /** Lo que se cerro en una vuelta. */
    public record Cerradas(int salas, int partidas) {
    }

    public Cerradas ejecutar() {
        Instant ahora = reloj.instant();
        Instant limite = ahora.minus(plazo);
        return new Cerradas(cerrarSalas(limite), cerrarPartidas(limite, ahora));
    }

    private int cerrarSalas(Instant limite) {
        int cerradas = 0;
        for (Sala sala : salas.sinEmpezarDesde(limite, LOTE)) {
            try {
                if (cancelar.porAbandono(sala.id())) {
                    cerradas++;
                    BITACORA.info("Sala {} cerrada por abandono: creada {} sin llegar a jugarse; apuesta devuelta",
                            sala.id(), sala.creadaEn());
                }
            } catch (RuntimeException fallo) {
                BITACORA.warn("No se pudo cerrar la sala abandonada {}: {}; se reintenta en la proxima vuelta",
                        sala.id(), fallo.getMessage());
            }
        }
        return cerradas;
    }

    private int cerrarPartidas(Instant limite, Instant ahora) {
        int cerradas = 0;
        for (Partida partida : partidas.enCursoDesde(limite, LOTE)) {
            try {
                if (cerrar(partida, ahora)) {
                    cerradas++;
                    BITACORA.info("Partida {} (sala {}) cerrada por abandono: iniciada {} y sin terminar; "
                            + "apuesta liquidada como empate", partida.id(), partida.idSala(), partida.iniciadaEn());
                }
            } catch (RuntimeException fallo) {
                BITACORA.warn("No se pudo cerrar la partida abandonada {}: {}; se reintenta en la proxima vuelta",
                        partida.id(), fallo.getMessage());
            }
        }
        return cerradas;
    }

    /**
     * Termina la partida sin ganador, deja la sala FINALIZADA, liquida la
     * apuesta (empate: cada uno recupera lo suyo) y lo anuncia. Si otra
     * escritura se adelanta —alguien jugo justo ahora—, no se toca: ya no esta
     * abandonada.
     */
    private boolean cerrar(Partida partida, Instant ahora) {
        partida.terminar(ahora);
        Partida guardada;
        try {
            guardada = partidas.guardar(partida);
        } catch (PartidaModificadaConcurrentemente alguienJugo) {
            return false;
        }
        salas.buscarPorId(guardada.idSala()).ifPresent(sala -> {
            if (sala.terminarPartida()) {
                try {
                    salas.guardar(sala);
                } catch (SalaModificadaConcurrentemente otroLaCerro) {
                    // La partida es la verdad; la sala la alcanza el siguiente aviso.
                }
            }
        });
        List<RepartoDeCreditos> reparto = apuesta.alTerminar(guardada);
        canal.anunciarFin(guardada, reparto);
        return true;
    }
}
