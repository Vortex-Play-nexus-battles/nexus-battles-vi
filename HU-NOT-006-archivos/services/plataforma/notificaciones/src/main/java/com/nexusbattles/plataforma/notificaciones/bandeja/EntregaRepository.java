package com.nexusbattles.plataforma.notificaciones.bandeja;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EntregaRepository extends JpaRepository<RegistroDeEntrega, Long> {

    List<RegistroDeEntrega> findByUsuarioId(String usuarioId);

    boolean existsByUsuarioIdAndAvisoIdAndSesionId(String usuarioId, String avisoId, String sesionId);
}
