package com.nexusbattles.plataforma.salaspartidas.dominio;

import com.nexusbattles.comun.error.ErrorDeNegocio;

import java.net.URI;

/**
 * El motor de combate no contesta — RF-JUE-006.
 *
 * <p>503 y no un resultado inventado. Resolver el ataque con un dano de nuestra
 * cosecha decidiria el combate con numeros falsos, y el sintoma aparecería
 * mucho despues —una barra de vida que no cuadra— sin rastro de la causa.
 *
 * <p>Mismo criterio que {@code InventarioNoDisponible}: cuando el dueno de una
 * regla no responde, se dice, no se suple.
 */
public class MotorNoDisponible extends ErrorDeNegocio {

    public static final URI TIPO =
            URI.create("https://nexusbattles.local/errores/motor-de-combate-no-disponible");

    public MotorNoDisponible(String detalleTecnico) {
        super(TIPO,
              "El combate no esta disponible ahora mismo",
              503,
              "No se pudo resolver la accion: " + detalleTecnico
                      + ". Vuelve a intentarlo en unos segundos.");
    }
}
