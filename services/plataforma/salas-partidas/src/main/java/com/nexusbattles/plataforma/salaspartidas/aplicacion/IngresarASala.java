package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.CanalDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import com.nexusbattles.plataforma.salaspartidas.dominio.SalaNoEncontrada;

import java.util.Objects;
import java.util.UUID;

/**
 * Ingreso de un jugador a una sala existente — HU-SAL-002, RF-JUE-002.
 *
 * <p>Coordina cuatro pasos y nada mas: buscar la sala, pedirle que admita al
 * jugador, guardar el resultado y anunciarlo a quienes ya estan dentro. Las
 * reglas de quien puede entrar viven en {@link Sala#unirse(UUID)}, no aqui: si
 * una condicion de ingreso apareciera en esta clase, estaria en el sitio
 * equivocado.
 *
 * <p><b>No comprueba el heroe equipado ni los creditos.</b> RF-JUE-003 exige lo
 * primero y RF-JUE-014 lo segundo, pero ambos dependen de modulos de otros
 * equipos que todavia no exponen contrato. Cuando existan, se anaden como
 * puertos igual que se hizo con los creditos en HU-SAL-001.
 */
public class IngresarASala {

    private final RepositorioDeSalas repositorio;
    private final CanalDeSala canal;

    public IngresarASala(RepositorioDeSalas repositorio, CanalDeSala canal) {
        this.repositorio = Objects.requireNonNull(repositorio);
        this.canal = Objects.requireNonNull(canal);
    }

    /**
     * @param idSala    sala elegida del listado
     * @param idJugador jugador autenticado que quiere entrar
     * @return la sala con el jugador dentro
     * @throws SalaNoEncontrada si el identificador no corresponde a ninguna sala
     */
    public Sala ejecutar(UUID idSala, UUID idJugador) {
        Objects.requireNonNull(idSala, "Hace falta la sala a la que se quiere entrar.");
        Objects.requireNonNull(idJugador, "Hace falta el jugador que quiere entrar.");

        Sala sala = repositorio.buscarPorId(idSala)
                .orElseThrow(() -> new SalaNoEncontrada(idSala));

        // Si unirse rechaza, la excepcion sale antes de guardar: una sala que no
        // admitio a nadie no tiene por que reescribirse, ni anunciarse.
        sala.unirse(idJugador);

        Sala guardada = repositorio.guardar(sala);

        // El anuncio va DESPUES de guardar, y solo si guardar salio bien. Al
        // reves se anunciaria una entrada que todavia podria perderse, y los
        // que estan dentro verian una ocupacion que la base de datos no tiene.
        canal.anunciarIngreso(guardada, idJugador);

        return guardada;
    }
}
