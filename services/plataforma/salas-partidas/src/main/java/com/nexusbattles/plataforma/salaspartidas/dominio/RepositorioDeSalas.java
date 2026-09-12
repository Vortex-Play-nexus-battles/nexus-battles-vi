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
}
