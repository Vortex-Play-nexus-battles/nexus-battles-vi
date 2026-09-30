package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;

import java.util.List;

/**
 * Puerto: con que heroe juega la maquina — decision D-B7-11.
 *
 * <p>§7.6 habla de «un heroe aleatorio controlado por la IA». La
 * implementacion sortea un prototipo del catalogo de heroes, SIN sanadores (un
 * sanador no inflige dano, §6.1.1: no podria ganar), en el nivel pedido y sin
 * equipamiento, con {@code SecureRandom}.
 */
public interface HeroesDeLaMaquina {

    /**
     * @param cuantos cupos de la maquina en la sala
     * @param nivel   nivel del heroe del anfitrion
     * @return un heroe por cupo, a vida completa en ese nivel; menos (o
     *         ninguno) si el catalogo no responde: los que falten combaten con
     *         una copia del heroe del anfitrion
     */
    List<HeroeDeCombate> elegir(int cuantos, int nivel);
}
