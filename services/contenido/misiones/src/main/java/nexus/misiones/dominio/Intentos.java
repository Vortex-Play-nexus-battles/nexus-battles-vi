package nexus.misiones.dominio;

import java.util.Objects;

/**
 * «Limite de intentos diarios o semanales» de los desafios (7.8.2). El
 * documento no da cifras: cada desafio declara las suyas en la semilla. Un
 * intento se consume al matricular, abandone o no el jugador despues.
 */
public record Intentos(int maximo, Periodo periodo) {

    public Intentos {
        if (maximo < 1) {
            throw new IllegalArgumentException("Un desafio permite al menos un intento por periodo.");
        }
        Objects.requireNonNull(periodo, "Los intentos se renuevan cada dia o cada semana.");
    }
}
