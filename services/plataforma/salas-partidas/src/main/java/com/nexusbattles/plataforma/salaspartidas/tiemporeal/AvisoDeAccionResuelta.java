package com.nexusbattles.plataforma.salaspartidas.tiemporeal;

import com.nexusbattles.plataforma.salaspartidas.dominio.AccionResuelta;

import java.util.List;
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
 * <p>{@code efectosActivos} no se emite todavia: el esquema {@code Efecto} lo
 * alimenta el motor de combate y este servicio no tiene de donde sacarlo. Es
 * opcional en el contrato, asi que omitirlo no lo incumple.
 *
 * <p>Vive en {@code tiemporeal} y no en el dominio por lo mismo que
 * {@link AvisoDeIngreso}: es formato de cable. Publico, con records anidados
 * publicos, para que la serializacion no tenga que forzar accesos.
 */
public record AvisoDeAccionResuelta(String tipo, UUID idPartida, UUID idEjecutor,
                                    Accion accion, List<Afectado> afectados) {

    /** Valor constante del discriminador, fijado por el contrato. */
    public static final String TIPO = "partida.accion.resuelta";

    /** Esquema {@code accion}: {@code icono} puede ser null, como permite el contrato. */
    public record Accion(String codigo, String nombre, String icono) {
    }

    /** Un elemento de {@code afectados}. */
    public record Afectado(UUID idJugador, int vidaActual, int vidaMaxima, int diferencia) {
    }

    static AvisoDeAccionResuelta de(AccionResuelta resultado) {
        return new AvisoDeAccionResuelta(
                TIPO,
                resultado.idPartida(),
                resultado.idEjecutor(),
                new Accion(resultado.accion().codigo(), resultado.accion().nombre(),
                        resultado.accion().icono()),
                resultado.afectados().stream()
                        .map(a -> new Afectado(a.idJugador(), a.vidaActual(), a.vidaMaxima(),
                                a.diferencia()))
                        .toList());
    }
}
