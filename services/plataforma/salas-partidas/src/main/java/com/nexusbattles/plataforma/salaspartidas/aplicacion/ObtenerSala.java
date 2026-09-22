package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import com.nexusbattles.plataforma.salaspartidas.dominio.SalaNoEncontrada;

import java.util.Objects;
import java.util.UUID;

/**
 * Consulta de una sala concreta — operacion {@code obtenerSala} del contrato.
 *
 * <p>Es el endpoint al que apunta la cabecera {@code Location} que devuelve la
 * creacion, y el que necesita la vista de sala para pintarse cuando se llega a
 * ella por enlace directo, sin pasar por el listado.
 *
 * <p><b>No filtra por participante.</b> Ver una sala no es entrar en ella: el
 * listado ya muestra las mismas salas, privadas incluidas, con su aforo y su
 * recompensa. Lo que una sala privada protege es el ingreso, no su existencia.
 * El unico dato que si se protege es el codigo de invitacion, y eso lo decide
 * la capa de API, que es la que sabe quien pregunta.
 */
public class ObtenerSala {

    private final RepositorioDeSalas repositorio;

    public ObtenerSala(RepositorioDeSalas repositorio) {
        this.repositorio = Objects.requireNonNull(repositorio, "Hace falta un repositorio de salas.");
    }

    /**
     * @param idSala sala pedida
     * @return la sala tal como esta almacenada
     * @throws SalaNoEncontrada si el identificador no corresponde a ninguna sala
     */
    public Sala ejecutar(UUID idSala) {
        Objects.requireNonNull(idSala, "Hace falta la sala que se quiere consultar.");
        return repositorio.buscarPorId(idSala)
                .orElseThrow(() -> new SalaNoEncontrada(idSala));
    }
}
