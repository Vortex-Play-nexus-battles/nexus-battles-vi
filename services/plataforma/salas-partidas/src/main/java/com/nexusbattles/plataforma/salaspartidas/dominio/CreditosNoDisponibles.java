package com.nexusbattles.plataforma.salaspartidas.dominio;

import com.nexusbattles.comun.error.ErrorDeNegocio;

import java.net.URI;

/**
 * El libro de creditos no contesta — HU-JUE-014, CA-06.
 *
 * <p>503 y no una reserva inventada. Dejar pasar una apuesta que nadie aparto
 * dejaria una partida con creditos en juego que no respalda nadie, y el
 * sintoma —un ganador al que no se le puede pagar— apareceria mucho despues
 * sin rastro de la causa. Mismo criterio que {@link InventarioNoDisponible} y
 * {@link MotorNoDisponible}: cuando el dueno de una regla no responde, se dice.
 *
 * <p>El {@code type} es estable a proposito: la interfaz decide por el, y el
 * criterio de aceptacion lo exige asi.
 */
public class CreditosNoDisponibles extends ErrorDeNegocio {

    public static final URI TIPO =
            URI.create("https://nexusbattles.local/errores/creditos-no-disponibles");

    public CreditosNoDisponibles(String detalleTecnico) {
        super(TIPO,
              "El libro de creditos no esta disponible ahora mismo",
              503,
              "No se pudieron comprometer los creditos de la apuesta: " + detalleTecnico
                      + ". Nada quedo reservado; vuelve a intentarlo en unos segundos.");
    }
}
