package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Consultas sobre bloqueos_mensajes_directos; todas derivadas, ninguna nativa. */
interface BloqueosSpringData extends JpaRepository<BloqueoEntidad, BloqueoEntidad.Clave> {

    List<BloqueoEntidad> findByBloqueador(UUID bloqueador);

    List<BloqueoEntidad> findByBloqueado(UUID bloqueado);
}
