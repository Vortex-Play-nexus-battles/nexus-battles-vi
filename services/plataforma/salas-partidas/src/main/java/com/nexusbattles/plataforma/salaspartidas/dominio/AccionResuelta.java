package com.nexusbattles.plataforma.salaspartidas.dominio;

import java.util.List;
import java.util.Map;
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
 * <p>Este servicio <b>no resuelve</b> la accion: la resuelve el motor de
 * combate, y aqui se modela el resultado que entrega para que el canal de la
 * partida pueda anunciarlo a todos los participantes. Por eso el record valida
 * forma, no reglas de juego.
 *
 * <p>Los campos y sus limites son los del mensaje {@code AccionResuelta} de
 * {@code contracts/websocket/salas-partidas.yaml}: {@code vidaActual >= 0},
 * {@code vidaMaxima >= 1}, {@code codigo} y {@code nombre} obligatorios, el
 * resto opcional. Desde 1.5.0 (B7) la accion trae la pedida, si se jugo en
 * valor base, su tipo, si es epica y la tirada; y cada afectado, su poder, sus
 * recargas y sus efectos activos.
 *
 * @param idPartida  partida en la que ocurrio
 * @param idEjecutor jugador que ejecuto la accion (o quien causo un efecto por turno)
 * @param accion     que se hizo
 * @param afectados  como quedo cada heroe tocado por la accion; puede estar
 *                   vacia (una accion fallida no cambia ninguna barra)
 */
public record AccionResuelta(UUID idPartida, UUID idEjecutor, Accion accion,
                             List<Afectado> afectados) {

    /** Codigo de un efecto que actuo al empezar un turno (canal 1.5.0). */
    public static final String EFECTO_POR_TURNO = "EFECTO_POR_TURNO";

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
     *
     * @param accionPedida la que pidio el jugador, si no es la que se jugo (1.5.0)
     * @param enValorBase  falto poder y se jugo el valor base (§6.1.1)
     * @param tipo         ATAQUE, DEFENSA, SANACION... o EFECTO
     * @param esEpica      epica de la Tabla 20
     * @param potenciada   epica jugada por su heroe afin
     * @param ataque       la tirada, si golpeo
     */
    public record Accion(String codigo, String nombre, String icono, String accionPedida, boolean enValorBase,
                         String tipo, boolean esEpica, boolean potenciada, Tirada ataque) {

        public Accion {
            exigirTexto(codigo, "codigo");
            exigirTexto(nombre, "nombre");
        }

        /** Sin los campos de 1.5.0. */
        public Accion(String codigo, String nombre, String icono) {
            this(codigo, nombre, icono, null, false, null, false, false, null);
        }

        private static void exigirTexto(String valor, String campo) {
            if (valor == null || valor.isBlank()) {
                throw new IllegalArgumentException(
                        "La accion necesita " + campo + ": el contrato lo exige.");
            }
        }
    }

    /** La tirada de un ataque, para que el combate sea auditable (1.5.0). */
    public record Tirada(int ataqueResuelto, int defensaObjetivo, boolean acierta, Integer indiceTabla,
                         Integer porcentajeDano) {
    }

    /**
     * Como quedo un heroe tras la accion.
     *
     * <p>{@code diferencia} es negativa si fue dano y positiva si fue curacion.
     * El contrato la marca opcional en el cable; aqui es obligatoria porque un
     * resultado de combate siempre sabe cuanto cambio, y anunciar una vida
     * nueva sin decir cuanto se movio obliga al cliente a recordar la anterior.
     *
     * @param poderActual    poder tras la accion (1.5.0), o {@code null}
     * @param poderMaximo    poder maximo, o {@code null}
     * @param recargas       acciones en carga y turnos propios que les faltan
     * @param efectosActivos efectos que lleva encima
     */
    public record Afectado(UUID idJugador, int vidaActual, int vidaMaxima, int diferencia, Integer poderActual,
                           Integer poderMaximo, Map<String, Integer> recargas,
                           List<EstadoDeCombate.Efecto> efectosActivos) {

        public Afectado {
            Objects.requireNonNull(idJugador, "Un afectado es un jugador concreto.");
            if (vidaActual < 0) {
                throw new IllegalArgumentException("La vida actual no puede ser negativa.");
            }
            if (vidaMaxima < 1) {
                throw new IllegalArgumentException("La vida maxima debe ser al menos 1.");
            }
            recargas = recargas == null ? Map.of() : Map.copyOf(recargas);
            efectosActivos = efectosActivos == null ? List.of() : List.copyOf(efectosActivos);
        }

        /** Sin los campos de 1.5.0. */
        public Afectado(UUID idJugador, int vidaActual, int vidaMaxima, int diferencia) {
            this(idJugador, vidaActual, vidaMaxima, diferencia, null, null, Map.of(), List.of());
        }
    }
}
