package com.nexusbattles.plataforma.salaspartidas.dominio;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida hacia el almacen de salas.
 *
 * <p>Vive en el dominio, no en la capa de persistencia: es el dominio quien
 * declara que necesita guardar y recuperar salas, y es la infraestructura la que
 * se adapta. Asi el caso de uso se prueba sin base de datos y el motor relacional
 * se puede cambiar sin tocar una sola regla del juego.
 */
public interface RepositorioDeSalas {

    /**
     * Guarda la sala y devuelve el estado con el que quedo almacenada.
     *
     * @return la sala guardada; nunca {@code null}
     */
    Sala guardar(Sala sala);

    /** Recupera una sala por su identificador, si existe. */
    Optional<Sala> buscarPorId(UUID id);

    /**
     * Pagina las salas que se muestran en el listado — RF-JUE-002.
     *
     * <p>La regla de que entra va aqui y no escondida en una consulta:
     * aparecen los estados de {@link EstadoSala#delListado()}, que son los tres
     * que el componente {@code Tarjeta de sala} sabe pintar. <b>Las privadas si
     * aparecen</b>, con su insignia; lo que se les niega es el ingreso sin
     * invitacion, y eso lo decide el agregado, no el almacen.
     *
     * <p>Quien implemente este puerto tiene que respetarlo, y por eso se prueba
     * contra el doble en memoria y contra PostgreSQL.
     *
     * <p>Paginar es cosa del almacen: traer todo a memoria para cortar despues
     * dejaria de funcionar en cuanto haya salas de verdad.
     *
     * @param modalidad filtro opcional; {@code null} no filtra
     * @param estado    filtro opcional; {@code null} no filtra
     * @param pagina    numero de pagina, base 0
     * @param tamano    elementos por pagina
     */
    PaginaDeSalas listar(Modalidad modalidad, EstadoSala estado, int pagina, int tamano);
}
