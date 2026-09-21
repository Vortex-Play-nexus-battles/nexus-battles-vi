package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.CanalDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.LiquidacionDeApuesta;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepartoDeCreditos;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeLiquidaciones;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDePartidas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Cierra las liquidaciones que quedaron pendientes porque el libro de creditos
 * no respondio — HU-JUE-014, CA-06.
 *
 * <p>Se ejecuta de forma periodica (lo programa la configuracion, no esta
 * clase) y recorre las pendientes de la mas antigua a la mas nueva. Cuando una
 * se cierra, se vuelve a anunciar {@code partida.finalizada} con el reparto:
 * quien siguiera mirando la vista de resultado ve por fin cuanto gano o
 * perdio. Quien ya se fue lo vera en su saldo.
 *
 * <p>Un reintento que vuelve a fallar no lanza nada: queda anotado con su
 * motivo y se vuelve a intentar en la siguiente vuelta. Lo que no hace nunca
 * es descartar una pendiente.
 */
public class ReintentarLiquidaciones {

    private static final Logger BITACORA = LoggerFactory.getLogger(ReintentarLiquidaciones.class);

    /** Cuantas pendientes se atienden por vuelta; el resto esperan a la siguiente. */
    static final int LOTE = 20;

    private final RepositorioDeLiquidaciones liquidaciones;
    private final RepositorioDePartidas partidas;
    private final LiquidarApuesta liquidar;
    private final CanalDePartida canal;

    public ReintentarLiquidaciones(RepositorioDeLiquidaciones liquidaciones, RepositorioDePartidas partidas,
                                   LiquidarApuesta liquidar, CanalDePartida canal) {
        this.liquidaciones = Objects.requireNonNull(liquidaciones);
        this.partidas = Objects.requireNonNull(partidas);
        this.liquidar = Objects.requireNonNull(liquidar);
        this.canal = Objects.requireNonNull(canal);
    }

    /** @return cuantas liquidaciones se cerraron en esta vuelta */
    public int ejecutar() {
        int cerradas = 0;
        for (LiquidacionDeApuesta pendiente : liquidaciones.pendientes(LOTE)) {
            Optional<Partida> partida = partidas.buscarPorId(pendiente.idPartida());
            if (partida.isEmpty()) {
                BITACORA.error("La liquidacion pendiente apunta a la partida {}, que no existe; "
                        + "hay que revisarla a mano.", pendiente.idPartida());
                continue;
            }
            Optional<List<RepartoDeCreditos>> reparto = liquidar.reintentar(pendiente, partida.get());
            if (reparto.isPresent()) {
                cerradas++;
                canal.anunciarFin(partida.get(), reparto.get());
            }
        }
        return cerradas;
    }
}
