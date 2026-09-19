package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoDelHeroe;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeNoDisponible;
import com.nexusbattles.plataforma.salaspartidas.dominio.InventarioNoDisponible;

/**
 * La puerta de heroe — HU-SAL-003, RF-JUE-003, SCRUM-1074.
 *
 * <p>Tres casos de uso tienen que aplicar exactamente la misma regla antes de
 * dejar pasar a alguien: crear una sala, entrar a una, y arrancar el combate.
 * Escribirla tres veces es garantizar que un dia diverjan y que una de las tres
 * puertas quede entreabierta. Vive aqui, en un solo sitio, y los tres la llaman.
 *
 * <p>No es una clase de dominio: no decide <i>que</i> hace disponible a un
 * heroe —eso lo decide el inventario, que es su dueno (frontera de alcance del
 * issue #27)— solo traduce su veredicto en «pasa» o «no pasa».
 *
 * <p><b>Un fallo del inventario no abre la puerta.</b> {@link HeroeDelJugador}
 * lanza {@link InventarioNoDisponible} y esa excepcion se deja subir tal cual
 * hasta el 503. Tratarla como «disponible» para no bloquear el juego meteria en
 * combate a gente sin heroe cada vez que el inventario tosiera, y el sintoma
 * aparecería mucho despues, en la vista de batalla, sin rastro de la causa.
 */
final class PuertaDeHeroe {

    private PuertaDeHeroe() {
    }

    /**
     * Deja pasar, o explica por que no.
     *
     * @param heroes  puerto hacia el inventario
     * @param jugador jugador autenticado que pregunta por si mismo
     * @return el veredicto completo, con el heroe, para quien necesite guardarlo
     * @throws HeroeNoDisponible      si no tiene heroe equipado o el suyo ya combate
     * @throws InventarioNoDisponible si el inventario no contesta
     */
    static EstadoDelHeroe comprobar(HeroeDelJugador heroes, JugadorAutenticado jugador) {
        EstadoDelHeroe estado = heroes.consultar(jugador);

        if (!estado.puedeCombatir()) {
            throw new HeroeNoDisponible(estado);
        }

        return estado;
    }
}
