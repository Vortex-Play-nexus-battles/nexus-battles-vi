package com.nexusbattles.plataforma.notificaciones.bandeja;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SesionRepository extends JpaRepository<RegistroDeSesion, Long> {

    List<RegistroDeSesion> findByUsuarioId(String usuarioId);

    boolean existsByUsuarioIdAndSesionId(String usuarioId, String sesionId);

    void deleteByUsuarioIdAndSesionId(String usuarioId, String sesionId);
}
