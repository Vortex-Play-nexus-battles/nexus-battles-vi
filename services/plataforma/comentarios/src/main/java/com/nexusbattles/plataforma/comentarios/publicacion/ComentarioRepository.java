package com.nexusbattles.plataforma.comentarios.publicacion;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ComentarioRepository extends JpaRepository<RegistroDeComentario, String> {

    List<RegistroDeComentario> findByProductoIdOrderByFechaPublicacionAsc(String productoId);
}
