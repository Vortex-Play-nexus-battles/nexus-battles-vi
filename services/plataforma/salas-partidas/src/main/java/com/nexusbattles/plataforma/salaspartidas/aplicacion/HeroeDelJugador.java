package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoDelHeroe;
import com.nexusbattles.plataforma.salaspartidas.dominio.InventarioNoDisponible;

/**
 * Puerto de salida hacia el modulo de inventario — HU-SAL-003, RF-JUE-003.
 *
 * <p>Una sola pregunta: <i>¿con que heroe entraria este jugador, y puede?</i>
 * La respuesta la da el inventario, que es su dueno. Este servicio no guarda
 * heroes, no sabe que es estar equipado y no decide cuando un heroe esta
 * comprometido: la frontera de alcance del issue #27 lo dice con todas las
 * letras — RF-INV-009 y RF-MIS-012 resuelven la disponibilidad y aqui solo se
 * consume su respuesta.
 *
 * <p>Por eso el puerto devuelve un veredicto, no datos crudos: si devolviera la
 * lista de elementos del inventario, la regla de «que cuenta como equipado»
 * acabaria escrita en este servicio, que es justo donde no debe estar.
 */
public interface HeroeDelJugador {

    /**
     * Consulta el heroe con el que el jugador iria a combatir.
     *
     * @param jugador jugador autenticado que pregunta por si mismo
     * @return el veredicto del inventario; nunca {@code null}
     * @throws InventarioNoDisponible si el proveedor no responde o contesta algo
     *                                que no se puede interpretar. No se inventa
     *                                un resultado: la verificacion previa existe
     *                                para informar, y una respuesta falsa
     *                                informa mal.
     */
    EstadoDelHeroe consultar(JugadorAutenticado jugador);
}
