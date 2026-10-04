package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDePartidas;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import com.nexusbattles.plataforma.salaspartidas.dominio.SalaNoEncontrada;

import java.util.Objects;
import java.util.Optional;
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
    private final RepositorioDePartidas partidas;

    public ObtenerSala(RepositorioDeSalas repositorio, RepositorioDePartidas partidas) {
        this.repositorio = Objects.requireNonNull(repositorio, "Hace falta un repositorio de salas.");
        this.partidas = Objects.requireNonNull(partidas, "Hace falta un repositorio de partidas.");
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

    /**
     * La partida de la sala, si ya arranco.
     *
     * <p>R18 — el contrato declara {@code idPartida} «nulo hasta que la partida
     * arranca», pero la respuesta lo mandaba nulo siempre, tambien despues. Sin
     * el, quien recargaba la pagina a mitad de combate volvia a la sala de
     * espera, con «Iniciar combate» (que responde 409) y sin forma de volver a
     * la partida, que seguia corriendo. Medido en dev el 24-sep.
     *
     * <p>Solo se busca cuando la sala ya jugo o esta jugando: en las demas no
     * puede haber partida, y asi la consulta de una sala abierta sigue costando
     * una sola lectura.
     *
     * @param sala la sala ya leida
     * @return el identificador de su partida, o vacio si no la tiene
     */
    public Optional<UUID> partidaDe(Sala sala) {
        Objects.requireNonNull(sala, "Hace falta la sala.");
        if (sala.estado() != EstadoSala.EN_JUEGO && sala.estado() != EstadoSala.FINALIZADA) {
            return Optional.empty();
        }
        return partidas.buscarPorSala(sala.id()).map(Partida::id);
    }
}