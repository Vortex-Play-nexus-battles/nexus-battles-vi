package com.nexusbattles.ms_subastas.pujas.service;

import java.time.Instant;

/**
 * Datos de participacion del jugador que el motor necesita para validar los
 * limites de 7.7.10. Los calcula quien llama al motor (tests, o
 * PujaApplicationService con sus repositorios); no se consultan aqui para
 * mantener el motor testeable sin base de datos.
 *
 * <p>Hasta B8 traia tambien en cuantas subastas participaba el jugador, para
 * un tope de 10 que se aplicaba a las pujas. Ese tope es de publicaciones
 * (ver MotorPujasService) y el campo se fue con el.
 *
 * @param ultimaPujaDelJugador   instante de su ultima puja EN ESTA SUBASTA, o null si no ha pujado en ella.
 *                               Es por subasta, no global: el intervalo de 5 s frena el spam dentro de una
 *                               subasta, no la participacion en varias a la vez
 * @param pujasActivasDelJugador cuantas de sus pujas siguen siendo la oferta vigente de su subasta
 */
public record ContextoParticipacion(Instant ultimaPujaDelJugador, int pujasActivasDelJugador) {

    public static ContextoParticipacion sinHistorial() {
        return new ContextoParticipacion(null, 0);
    }
}
