package com.nexusbattles.ms_subastas.pujas.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Los limites de participacion de 7.7.10 tal como los trae el entorno
 * ({@code app.pujas.*}): 10 subastas activas por jugador, 50 pujas activas y
 * 5 s entre pujas consecutivas.
 *
 * <p>Desde B8 no son la fuente de verdad sino su <b>respaldo</b>: los valores
 * vigentes salen de admin-parametros
 * ({@code subastas.max-subastas-activas-por-jugador},
 * {@code subastas.max-pujas-activas-por-jugador},
 * {@code subastas.intervalo-minimo-segundos}) por
 * {@code reglas.ReglasDesdeParametros}, y estos se usan cuando el catalogo no
 * esta configurado o no responde. Los valores por omision son los del
 * documento; el Project Charter los declara inalterables.
 */
@Component
@ConfigurationProperties(prefix = "app.pujas")
public class ParametrosPuja {

    private int maxSubastasActivasPorJugador = 10;
    private int maxPujasActivasPorJugador = 50;
    private int intervaloMinimoSegundos = 5;

    public int getMaxSubastasActivasPorJugador() {
        return maxSubastasActivasPorJugador;
    }

    public void setMaxSubastasActivasPorJugador(int maxSubastasActivasPorJugador) {
        this.maxSubastasActivasPorJugador = maxSubastasActivasPorJugador;
    }

    public int getMaxPujasActivasPorJugador() {
        return maxPujasActivasPorJugador;
    }

    public void setMaxPujasActivasPorJugador(int maxPujasActivasPorJugador) {
        this.maxPujasActivasPorJugador = maxPujasActivasPorJugador;
    }

    public int getIntervaloMinimoSegundos() {
        return intervaloMinimoSegundos;
    }

    public void setIntervaloMinimoSegundos(int intervaloMinimoSegundos) {
        this.intervaloMinimoSegundos = intervaloMinimoSegundos;
    }
}
