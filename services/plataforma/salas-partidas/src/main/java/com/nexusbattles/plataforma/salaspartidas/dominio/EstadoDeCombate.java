package com.nexusbattles.plataforma.salaspartidas.dominio;

import java.util.List;
import java.util.Map;

/**
 * Como esta un participante en el combate, tal como lo devolvio el motor — B7.
 *
 * <p>El motor de combate no guarda nada entre llamadas: recibe el estado de
 * todos, lo transforma y lo devuelve. Quien lo guarda es esta partida, y esto
 * es lo que guarda de cada participante ademas de su vida (que sigue en
 * {@link HeroeDeCombate}): poder, turnos propios, cargas, efectos y el ultimo
 * golpe recibido, que el motor necesita en la llamada siguiente, y lo que la
 * vista pinta y no tiene que recalcular —poder maximo, recargas y acciones
 * disponibles—.
 *
 * <p>Este servicio no interpreta nada de esto: ni que hace un efecto, ni cuando
 * vence una carga. Eso es del motor.
 *
 * @param poderActual        poder ahora
 * @param poderMaximo        poder maximo en su nivel
 * @param turnosJugados      turnos propios ya jugados
 * @param cargas             por accion o epica, {@code turnosJugados} cuando se uso
 * @param efectos            efectos activos
 * @param ultimoDanoRecibido ultimo golpe recibido, o {@code null}
 * @param recargas           acciones en carga y turnos propios que les faltan
 * @param acciones           lo que puede jugar ahora y por que no lo demas
 * @param estadisticas       estadisticas con las que resolvio el motor
 */
public record EstadoDeCombate(int poderActual, int poderMaximo, int turnosJugados, Map<String, Integer> cargas,
                              List<Efecto> efectos, GolpeRecibido ultimoDanoRecibido,
                              Map<String, Integer> recargas, List<AccionDisponible> acciones,
                              EstadisticasDeCombate estadisticas) {

    public EstadoDeCombate {
        if (poderActual < 0 || poderMaximo < 0 || turnosJugados < 0) {
            throw new IllegalArgumentException("Poder y turnos jugados no pueden ser negativos.");
        }
        cargas = cargas == null ? Map.of() : Map.copyOf(cargas);
        efectos = efectos == null ? List.of() : List.copyOf(efectos);
        recargas = recargas == null ? Map.of() : Map.copyOf(recargas);
        acciones = acciones == null ? List.of() : List.copyOf(acciones);
    }

    /**
     * Un efecto activo — esquema {@code EfectoActivo} del motor.
     *
     * @param tipo el {@code TipoDeEfecto} del motor, como texto: este servicio
     *             no lo interpreta, lo guarda y lo muestra
     */
    public record Efecto(String codigo, String nombre, String tipo, int valor, int turnos, boolean hastaSuTurno,
                         String origen) {
    }

    /** El ultimo golpe recibido y de quien («Pare de fuego» lo retorna). */
    public record GolpeRecibido(String de, int cantidad) {
    }

    /**
     * Una accion jugable y su disponibilidad — esquema {@code EstadoDeAccion},
     * calculado por el motor para que ni este servicio ni la interfaz
     * reimplementen la regla.
     */
    public record AccionDisponible(String codigo, String nombre, String tipo, boolean esEpica,
                                   Integer costoPoder, boolean todoElPoder, int turnosDeCarga,
                                   int nivelRequerido, boolean disponible, String motivo) {
    }
}
