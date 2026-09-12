package com.nexusbattles.ms_subastas.pujas.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Los 4 limites de participacion de HU-SUB-004, configurables desde
 * administracion (app.pujas.* en application.properties / variables de
 * entorno). Pendiente decidir si en el futuro los sirve admin-parametros
 * en vez de config estatica.
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
