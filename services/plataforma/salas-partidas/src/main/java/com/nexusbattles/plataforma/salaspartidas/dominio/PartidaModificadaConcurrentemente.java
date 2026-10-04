package com.nexusbattles.plataforma.salaspartidas.dominio;

import com.nexusbattles.comun.error.ErrorDeNegocio;

import java.net.URI;
import java.util.UUID;

/**
 * Dos escrituras de la misma partida a la vez — 409 {@code partida-modificada}.
 *
 * <p>Bloqueo optimista (B7): la partida lleva una marca de version y una
 * escritura hecha sobre una lectura vieja no pisa a la que ya entro. Es lo que
 * impide que dos acciones del mismo turno —un doble clic, dos pestanas— se
 * apliquen las dos: una entra y la otra recibe esto, sin que se aplique nada.
 */
public class PartidaModificadaConcurrentemente extends ErrorDeNegocio {

    public static final URI TIPO = URI.create("https://nexusbattles.local/errores/partida-modificada");

    private final UUID idPartida;

    public PartidaModificadaConcurrentemente(UUID idPartida) {
        super(TIPO, "La partida acaba de cambiar", 409,
                "Otra acción de esta partida se aplicó a la vez que la tuya y la tuya no se aplicó.");
        this.idPartida = idPartida;
    }

    public UUID idPartida() {
        return idPartida;
    }
}
