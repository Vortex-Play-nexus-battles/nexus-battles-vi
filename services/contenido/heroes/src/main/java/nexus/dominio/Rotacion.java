package nexus.dominio;

import java.util.List;

/**
 * Una rotacion de habilidades (seccion 7.8.5, p. 61): "secuencia priorizada de
 * habilidades que la inteligencia artificial ejecuta durante una mision" (ERS,
 * glosario). La prioridad la da la posicion: Rotacion 1 Alta, 2 Media, 3 Baja.
 * Cada paso es el nombre exacto de una accion de la Tabla 7 que el heroe posee
 * en su nivel, o el ataque basico.
 */
public record Rotacion(Prioridad prioridad, List<String> pasos) {

    public enum Prioridad {
        ALTA, MEDIA, BAJA;

        /** La prioridad que corresponde a la posicion (0 = Rotacion 1). */
        static Prioridad enPosicion(int indice) {
            return values()[indice];
        }
    }

    public Rotacion {
        pasos = List.copyOf(pasos);
    }
}
