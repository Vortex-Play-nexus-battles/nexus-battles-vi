package com.nexusbattles.plataforma.notificaciones.bandeja;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificacionRepository extends JpaRepository<RegistroDeNotificacion, Long> {

    List<RegistroDeNotificacion> findByUsuarioIdOrderByCreadaEnAsc(String usuarioId);

    Optional<RegistroDeNotificacion> findByUsuarioIdAndAvisoId(String usuarioId, String avisoId);

    boolean existsByUsuarioIdAndAvisoId(String usuarioId, String avisoId);
}
