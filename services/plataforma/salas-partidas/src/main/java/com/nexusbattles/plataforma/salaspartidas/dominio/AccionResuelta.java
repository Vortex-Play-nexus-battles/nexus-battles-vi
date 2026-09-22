package com.nexusbattles.plataforma.salaspartidas.dominio;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Resultado de una accion de combate, ya resuelto — HU-SAL-005 (RF-JUE-009).
 *
 * <p>Es el hecho que mueve las barras de vida: quien actuo, que hizo y con que
 * vida quedo cada afectado. Lleva <b>vida actual y vida maxima</b>, nunca un
 * color ni un porcentaje: el umbral del 60 % y del 40 % lo aplica el cliente
 * con las fichas de diseno (criterio 1 del issue #31), y si el servidor lo
 * calculara habria dos sitios que pudieran discrepar.
 *
 * <p>Este servicio <b>no resuelve</b> la accion. Calcular el dano, los efectos y
 * el turno siguiente es del motor de combate, que el Project Charter excluye de
 * este bloque. Aqui solo se modela el resultado que ese motor entrega, para que
 * el canal de la partida pueda anunciarlo a todos los participantes (criterio
 * 3). Por eso el record valida forma, no reglas de juego.
 *
 * <p>Los campos y sus limites son los del mensaje {@code AccionResuelta} de
 * {@code contracts/websocket/salas-partidas.yaml}: {@code vidaActual >= 0},
 * {@code vidaMaxima >= 1}, {@code codigo} y {@code nombre} obligatorios,
 * {@code icono} opcional (RF-JUE-017 pide icono para efectos y controles, pero
 * no todas las acciones lo tienen todavia).
 *
 * @param idPartida  partida en la que ocurrio
 * @param idEjecutor jugador que ejecuto la accion
 * @param accion     que se hizo, tal como lo nombra el motor de combate
 * @param afectados  vida resultante de cada heroe tocado por la accion; puede
 *                   estar vacia (una accion fallida no cambia ninguna barra)
 */
public record AccionResuelta(UUID idPartida, UUID idEjecutor, Accion accion,
                             List<Afectado> afectados) {

    public AccionResuelta {
        Objects.requireNonNull(idPartida, "Una accion resuelta pertenece a una partida.");
        Objects.requireNonNull(idEjecutor, "Una accion resuelta tiene un ejecutor.");
        Objects.requireNonNull(accion, "Una accion resuelta dice que accion fue.");
        Objects.requireNonNull(afectados, "Los afectados pueden ser ninguno, pero no null.");
        afectados = List.copyOf(afectados);
    }

    /**
     * La accion ejecutada. {@code icono} puede ser null: el contrato lo declara
     * {@code [string, 'null']}.
     */
    public record Accion(String codigo, String nombre, String icono) {

        public Accion {
            exigirTexto(codigo, "codigo");
            exigirTexto(nombre, "nombre");
        }

        private static void exigirTexto(String valor, String campo) {
            if (valor == null || valor.isBlank()) {
                throw new IllegalArgumentException(
                        "La accion necesita " + campo + ": el contrato lo exige.");
            }
        }
    }

    /**
     * Vida con la que quedo un heroe tras la accion.
     *
     * <p>{@code diferencia} es negativa si fue dano y positiva si fue curacion.
     * El contrato la marca opcional en el cable; aqui es obligatoria porque un
     * resultado de combate siempre sabe cuanto cambio, y anunciar una vida
     * nueva sin decir cuanto se movio obliga al cliente a recordar la anterior.
     */
    public record Afectado(UUID idJugador, int vidaActual, int vidaMaxima, int diferencia) {

        public Afectado {
            Objects.requireNonNull(idJugador, "Un afectado es un jugador concreto.");
            if (vidaActual < 0) {
                throw new IllegalArgumentException("La vida actual no puede ser negativa.");
            }
            if (vidaMaxima < 1) {
                throw new IllegalArgumentException("La vida maxima debe ser al menos 1.");
            }
        }
    }
}
