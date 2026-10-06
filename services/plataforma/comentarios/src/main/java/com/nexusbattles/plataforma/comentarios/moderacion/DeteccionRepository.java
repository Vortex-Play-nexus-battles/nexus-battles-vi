package com.nexusbattles.plataforma.comentarios.moderacion;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Las detecciones del filtro automatico — HU-COM-007 CA-01. La clave es el
 * comentario: la cola las pide todas en una consulta ({@code findAllById}) y
 * el detalle la suya ({@code findById}).
 */
public interface DeteccionRepository extends JpaRepository<RegistroDeDeteccion, String> {
}
