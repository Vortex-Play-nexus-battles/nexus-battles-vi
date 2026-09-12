package com.nexusbattles.ms_subastas.pujas.service;

import java.time.Instant;

/**
 * Datos de participacion del jugador que el motor necesita para validar los
 * 4 limites de RF-SUB-004. Hoy los calcula quien llama al motor (tests, o mas
 * adelante un repositorio JPA); no se consultan aqui para mantener el motor
 * testeable sin base de datos.
 *
 * @param ultimaPujaDelJugador        instante de su ultima puja en cualquier subasta, o null si nunca pujo
 * @param pujasActivasDelJugador      cuantas de sus pujas siguen siendo la oferta vigente de su subasta
 * @param subastasActivasDelJugador   en cuantas subastas distintas participa activamente (sin contar esta, si ya estaba en ella)
 */
public record ContextoParticipacion(Instant ultimaPujaDelJugador, int pujasActivasDelJugador, int subastasActivasDelJugador) {

    public static ContextoParticipacion sinHistorial() {
        return new ContextoParticipacion(null, 0, 0);
    }
}
