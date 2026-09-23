package com.nexusbattles.plataforma.salaspartidas.sanciones;

import java.util.UUID;

/**
 * Si un jugador tiene una sancion activa — RF-USR-004, consultado contra
 * moderacion-sanciones.
 *
 * <h2>Por que ya no vive en el paquete del chat</h2>
 *
 * Hasta R10.2 este puerto estaba en {@code chat} y su metodo se llamaba
 * {@code estaSilenciado}, porque el unico sitio que preguntaba era el chat de
 * la sala. Eso convertia una pregunta sobre <b>el jugador</b> en una pregunta
 * sobre <b>el chat</b>, y la consecuencia no era estetica: cuando se busco
 * donde mas habia que comprobar la sancion, el puerto parecia una pieza del
 * chat y no la puerta de toda la casa.
 *
 * <p>La respuesta sigue siendo un solo hecho —hay sancion activa o no—,
 * porque es lo unico que el contrato de consulta promete (decision D-14).
 * Que hacer con ese hecho lo decide cada caso de uso: el chat silencia, las
 * puertas de sala rechazan.
 */
public interface SancionesDelJugador {

    /**
     * @param idJugador el {@code uid} estable del token (ADR-002)
     * @return true si hay una sancion que restringe ahora mismo
     * @throws SancionesNoDisponibles si no se pudo comprobar; nunca se
     *     devuelve {@code false} por no haber podido preguntar
     */
    boolean tieneSancionActiva(UUID idJugador);
}
