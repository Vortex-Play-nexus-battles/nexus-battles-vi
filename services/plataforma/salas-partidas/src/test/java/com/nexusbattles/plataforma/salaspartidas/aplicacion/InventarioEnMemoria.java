package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoDelHeroe;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.InventarioNoDisponible;

import java.util.ArrayList;
import java.util.List;

/**
 * Inventario de mentira para las pruebas de la puerta de heroe (SCRUM-1074).
 *
 * <p>Responde siempre lo mismo y anota a quien le preguntaron. Lo segundo
 * importa tanto como lo primero: media puerta es comprobar el veredicto, y la
 * otra media es comprobar que se pregunta por el jugador del token y no por
 * otro.
 */
public class InventarioEnMemoria implements HeroeDelJugador {

    /** Heroe cualquiera, valido. Los detalles no importan para la puerta. */
    public static final HeroeDeCombate SOMBRA =
            new HeroeDeCombate("h-1", "Sombra de Vael", null, 7, 140, 140);

    private final EstadoDelHeroe respuesta;
    private final RuntimeException fallo;

    /** A quien se le pregunto, en orden. */
    public final List<JugadorAutenticado> consultados = new ArrayList<>();

    private InventarioEnMemoria(EstadoDelHeroe respuesta, RuntimeException fallo) {
        this.respuesta = respuesta;
        this.fallo = fallo;
    }

    /** El jugador tiene su heroe listo: la puerta deja pasar. */
    public static InventarioEnMemoria conHeroe() {
        return new InventarioEnMemoria(EstadoDelHeroe.disponible(SOMBRA), null);
    }

    /** No hay heroe equipado: la puerta rechaza. */
    public static InventarioEnMemoria sinHeroe() {
        return new InventarioEnMemoria(EstadoDelHeroe.sinHeroeEquipado(), null);
    }

    /** El heroe ya combate en otro sitio: la puerta rechaza. */
    public static InventarioEnMemoria conHeroeOcupado(String donde) {
        return new InventarioEnMemoria(EstadoDelHeroe.ocupado(SOMBRA, donde), null);
    }

    /** El inventario no contesta: la puerta NO se abre por defecto. */
    public static InventarioEnMemoria caido() {
        return new InventarioEnMemoria(null, new InventarioNoDisponible("apagado"));
    }

    @Override
    public EstadoDelHeroe consultar(JugadorAutenticado jugador) {
        consultados.add(jugador);
        if (fallo != null) {
            throw fallo;
        }
        return respuesta;
    }

    /** Cuantas veces se molesto al inventario. */
    public int vecesConsultado() {
        return consultados.size();
    }
}
