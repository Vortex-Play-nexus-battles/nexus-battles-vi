package com.nexusbattles.plataforma.salaspartidas.dominio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Almacen de liquidaciones de apuesta — puerto de salida, HU-JUE-014.
 *
 * <p>Mismo criterio que {@link RepositorioDePartidas}: el dominio declara que
 * necesita recordar que deudas quedan por cobrar, y la infraestructura elige
 * donde.
 */
public interface RepositorioDeLiquidaciones {

    /** Guarda o actualiza la liquidacion de una partida (una por partida). */
    LiquidacionDeApuesta guardar(LiquidacionDeApuesta liquidacion);

    /** La liquidacion de una partida, si alguna vez se intento. */
    Optional<LiquidacionDeApuesta> buscarPorPartida(UUID idPartida);

    /**
     * Las que siguen {@link LiquidacionDeApuesta.Estado#PENDIENTE}, de la mas
     * antigua a la mas nueva, hasta {@code maximo}.
     */
    List<LiquidacionDeApuesta> pendientes(int maximo);
}
