package com.nexusbattles.plataforma.salaspartidas.tiemporeal;

import com.nexusbattles.plataforma.salaspartidas.dominio.AccionResuelta;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mensaje {@code partida.accion.resuelta} del AsyncAPI, tal cual viaja.
 *
 * <p>Calcado del mensaje {@code AccionResuelta} en
 * {@code contracts/websocket/salas-partidas.yaml}: discriminador, partida,
 * ejecutor, accion y la lista de afectados con su vida. Viaja {@code vidaActual}
 * y {@code vidaMaxima} y <b>nunca un color</b>: el umbral lo aplica el cliente
 * (RF-JUE-009).
 *
 * <p>Desde 1.5.0 (B7) la accion trae la pedida, si se jugo en valor base, su
 * tipo, si es epica y la tirada; y cada afectado su poder, sus recargas y sus
 * efectos activos, que alimenta el motor de combate.
 *
 * <p>Desde 1.7.0 trae a quien apunto la accion ({@code idObjetivo}) y, por cada
 * afectado, por que cambio su vida ({@code causas}): un reflejo de los Pinchos
 * de escudo deja de parecer un golpe que el atacante se dio a si mismo.
 *
 * <p>Vive en {@code tiemporeal} y no en el dominio por lo mismo que
 * {@link AvisoDeIngreso}: es formato de cable. Publico, con records anidados
 * publicos, para que la serializacion no tenga que forzar accesos.
 */
public record AvisoDeAccionResuelta(String tipo, UUID idPartida, UUID idEjecutor,
                                    Accion accion, List<Afectado> afectados, UUID idObjetivo) {

    /** Valor constante del discriminador, fijado por el contrato. */
    public static final String TIPO = "partida.accion.resuelta";

    /** Sin el objetivo de 1.7.0. */
    public AvisoDeAccionResuelta(String tipo, UUID idPartida, UUID idEjecutor, Accion accion,
                                 List<Afectado> afectados) {
        this(tipo, idPartida, idEjecutor, accion, afectados, null);
    }

    /** Por que cambio la vida de un afectado (1.7.0), tal como lo anoto el motor. */
    public record Causa(String tipo, UUID origen, String efecto, Integer cantidad) {
    }

    /** Esquema {@code accion}: {@code icono} puede ser null, como permite el contrato. */
    public record Accion(String codigo, String nombre, String icono, String accionPedida, boolean enValorBase,
                         String tipo, boolean esEpica, boolean potenciada, Tirada ataque) {

        /** Sin los campos de 1.5.0. */
        public Accion(String codigo, String nombre, String icono) {
            this(codigo, nombre, icono, null, false, null, false, false, null);
        }
    }

    /** La tirada de un ataque (1.5.0). */
    public record Tirada(int ataqueResuelto, int defensaObjetivo, boolean acierta, Integer indiceTabla,
                         Integer porcentajeDano) {
    }

    /** Un elemento de {@code afectados}. */
    public record Afectado(UUID idJugador, int vidaActual, int vidaMaxima, int diferencia,
                           List<EfectoEnCable> efectosActivos, Integer poderActual, Integer poderMaximo,
                           Map<String, Integer> recargas, List<Causa> causas) {

        /** Sin las causas de 1.7.0. */
        public Afectado(UUID idJugador, int vidaActual, int vidaMaxima, int diferencia,
                        List<EfectoEnCable> efectosActivos, Integer poderActual, Integer poderMaximo,
                        Map<String, Integer> recargas) {
            this(idJugador, vidaActual, vidaMaxima, diferencia, efectosActivos, poderActual, poderMaximo, recargas,
                    List.of());
        }

        /** Sin los campos de 1.5.0. */
        public Afectado(UUID idJugador, int vidaActual, int vidaMaxima, int diferencia) {
            this(idJugador, vidaActual, vidaMaxima, diferencia, List.of(), null, null, Map.of(), List.of());
        }
    }

    static AvisoDeAccionResuelta de(AccionResuelta resultado) {
        AccionResuelta.Accion accion = resultado.accion();
        AccionResuelta.Tirada tirada = accion.ataque();
        return new AvisoDeAccionResuelta(
                TIPO,
                resultado.idPartida(),
                resultado.idEjecutor(),
                new Accion(accion.codigo(), accion.nombre(), accion.icono(), accion.accionPedida(),
                        accion.enValorBase(), accion.tipo(), accion.esEpica(), accion.potenciada(),
                        tirada == null ? null : new Tirada(tirada.ataqueResuelto(), tirada.defensaObjetivo(),
                                tirada.acierta(), tirada.indiceTabla(), tirada.porcentajeDano())),
                resultado.afectados().stream()
                        .map(a -> new Afectado(a.idJugador(), a.vidaActual(), a.vidaMaxima(), a.diferencia(),
                                EfectoEnCable.de(a.efectosActivos()), a.poderActual(), a.poderMaximo(),
                                a.recargas(), a.causas().stream()
                                        .map(c -> new Causa(c.tipo(), c.origen(), c.efecto(), c.cantidad()))
                                        .toList()))
                        .toList(),
                resultado.idObjetivo());
    }
}
