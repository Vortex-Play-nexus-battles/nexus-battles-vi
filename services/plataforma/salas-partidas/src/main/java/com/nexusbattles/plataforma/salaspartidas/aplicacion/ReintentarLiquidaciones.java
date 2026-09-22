package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.CanalDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.CreditoPorPartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.LiquidacionDeApuesta;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.RecompensaDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepartoDeCreditos;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeLiquidaciones;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDePartidas;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeRecompensas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Cierra lo que quedo pendiente con el libro de creditos porque no respondio:
 * las liquidaciones de apuesta (HU-JUE-014, CA-06) y las recompensas por
 * jugar (HU-JUE-012, CA-05).
 *
 * <p>Se ejecuta de forma periodica (lo programa la configuracion, no esta
 * clase) y recorre las pendientes de la mas antigua a la mas nueva. Cuando una
 * se cierra, se vuelve a anunciar {@code partida.finalizada} con lo que
 * faltaba: quien siguiera mirando la vista de resultado ve por fin cuanto gano
 * o perdio. Quien ya se fue lo vera en su saldo.
 *
 * <p>Un reintento que vuelve a fallar no lanza nada: queda anotado con su
 * motivo y se vuelve a intentar en la siguiente vuelta. Lo que no hace nunca
 * es descartar una pendiente.
 */
public class ReintentarLiquidaciones {

    private static final Logger BITACORA = LoggerFactory.getLogger(ReintentarLiquidaciones.class);

    /** Cuantas pendientes de cada tipo se atienden por vuelta; el resto esperan a la siguiente. */
    static final int LOTE = 20;

    private final RepositorioDeLiquidaciones liquidaciones;
    private final RepositorioDeRecompensas recompensas;
    private final RepositorioDePartidas partidas;
    private final LiquidarApuesta liquidar;
    private final AcreditarRecompensa acreditar;
    private final CanalDePartida canal;

    public ReintentarLiquidaciones(RepositorioDeLiquidaciones liquidaciones,
                                   RepositorioDeRecompensas recompensas,
                                   RepositorioDePartidas partidas,
                                   LiquidarApuesta liquidar, AcreditarRecompensa acreditar,
                                   CanalDePartida canal) {
        this.liquidaciones = Objects.requireNonNull(liquidaciones);
        this.recompensas = Objects.requireNonNull(recompensas);
        this.partidas = Objects.requireNonNull(partidas);
        this.liquidar = Objects.requireNonNull(liquidar);
        this.acreditar = Objects.requireNonNull(acreditar);
        this.canal = Objects.requireNonNull(canal);
    }

    /** @return cuantas pendientes (apuestas y recompensas) se cerraron en esta vuelta */
    public int ejecutar() {
        return cerrarApuestas() + cerrarRecompensas();
    }

    private int cerrarApuestas() {
        int cerradas = 0;
        for (LiquidacionDeApuesta pendiente : liquidaciones.pendientes(LOTE)) {
            Optional<Partida> partida = partidaDe(pendiente.idPartida(), "liquidacion");
            if (partida.isEmpty()) {
                continue;
            }
            Optional<List<RepartoDeCreditos>> reparto = liquidar.reintentar(pendiente, partida.get());
            if (reparto.isPresent()) {
                cerradas++;
                canal.anunciarFin(partida.get(), reparto.get(), List.of());
            }
        }
        return cerradas;
    }

    private int cerrarRecompensas() {
        int cerradas = 0;
        for (RecompensaDePartida pendiente : recompensas.pendientes(LOTE)) {
            Optional<Partida> partida = partidaDe(pendiente.idPartida(), "recompensa");
            if (partida.isEmpty()) {
                continue;
            }
            Optional<List<CreditoPorPartida>> premio = acreditar.reintentar(pendiente, partida.get());
            if (premio.isPresent()) {
                cerradas++;
                if (!premio.get().isEmpty()) {
                    canal.anunciarFin(partida.get(), List.of(), premio.get());
                }
            }
        }
        return cerradas;
    }

    private Optional<Partida> partidaDe(java.util.UUID idPartida, String que) {
        Optional<Partida> partida = partidas.buscarPorId(idPartida);
        if (partida.isEmpty()) {
            BITACORA.error("La {} pendiente apunta a la partida {}, que no existe; "
                    + "hay que revisarla a mano.", que, idPartida);
        }
        return partida;
    }
}
