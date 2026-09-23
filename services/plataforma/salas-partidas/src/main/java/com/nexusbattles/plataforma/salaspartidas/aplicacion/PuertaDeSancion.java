package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.JugadorSancionado;
import com.nexusbattles.plataforma.salaspartidas.sanciones.SancionesDelJugador;

/**
 * La puerta que faltaba: un sancionado no entra a jugar — HU-USR-005/006.
 *
 * <h2>Donde se pone, y donde NO</h2>
 *
 * Se comprueba en las <b>puertas</b>: crear una sala y entrar a una sala. No
 * se comprueba en cada turno de combate, y eso es deliberado por dos motivos
 * que apuntan en la misma direccion:
 *
 * <ul>
 *   <li><b>Latencia.</b> Cada accion de combate va por el canal en tiempo
 *       real con un presupuesto de 500 ms extremo a extremo (HU-REN-001).
 *       Meter una llamada HTTP sincrona a otro servicio en ese camino es
 *       gastarse el presupuesto en una comprobacion que la puerta ya hizo.</li>
 *   <li><b>Producto.</b> Sancionar a alguien a mitad de una partida y
 *       echarlo del combate deja a sus rivales con una partida rota y unos
 *       creditos apostados a medias. La sancion le impide empezar otra;
 *       mientras tanto el chat de esa misma sala ya lo silencia.</li>
 * </ul>
 *
 * <p>Si el Product Owner decide que una sancion debe expulsar en caliente,
 * eso es una funcionalidad distinta —cerrar partidas en curso, devolver
 * apuestas— y no una linea mas en este metodo.
 *
 * <p>Mismo patron que {@link PuertaDeHeroe}: una clase de utilidad, no un
 * bean, porque no tiene estado y el caso de uso la llama explicitamente. Asi
 * se lee en el propio caso de uso que hay dos puertas y en que orden.
 */
final class PuertaDeSancion {

    private PuertaDeSancion() {
    }

    /**
     * @throws JugadorSancionado si tiene una sancion activa (403)
     * @throws com.nexusbattles.plataforma.salaspartidas.sanciones.SancionesNoDisponibles
     *     si no se pudo comprobar (503, fail-closed)
     */
    static void comprobar(SancionesDelJugador sanciones, JugadorAutenticado jugador) {
        if (sanciones.tieneSancionActiva(jugador.id())) {
            throw new JugadorSancionado();
        }
    }
}
