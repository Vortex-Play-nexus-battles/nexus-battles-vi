package com.nexusbattles.plataforma.salaspartidas.dominio;

import com.nexusbattles.comun.error.ErrorDeNegocio;

import java.net.URI;

/**
 * El jugador no tiene saldo para comprometer la recompensa de la sala.
 *
 * <p><b>RF-JUE-014</b> permite poner creditos en juego; al crear la sala se
 * comprometen. Si no alcanzan, el mensaje dice cuantos hay y cuantos hacen falta,
 * porque el requisito obliga a comunicar el motivo del rechazo.
 *
 * <p>El texto reproduce el ejemplo del contrato OpenAPI para que interfaz y
 * servidor no se desincronicen.
 */
public class CreditosInsuficientes extends ErrorDeNegocio {

    public static final URI TIPO =
            URI.create("https://nexusbattles.local/errores/creditos-insuficientes");

    private final int disponibles;
    private final int requeridos;

    public CreditosInsuficientes(int disponibles, int requeridos) {
        super(TIPO,
              "Creditos insuficientes",
              422,
              "Tienes " + disponibles + " creditos y necesitas " + requeridos
                      + " para crear esta sala.");
        this.disponibles = disponibles;
        this.requeridos = requeridos;
    }

    public int disponibles() {
        return disponibles;
    }

    public int requeridos() {
        return requeridos;
    }
}
