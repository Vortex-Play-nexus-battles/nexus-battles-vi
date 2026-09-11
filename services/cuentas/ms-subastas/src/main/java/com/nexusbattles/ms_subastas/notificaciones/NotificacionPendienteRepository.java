package com.nexusbattles.ms_subastas.notificaciones;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificacionPendienteRepository extends JpaRepository<NotificacionPendiente, UUID> {

    /** Lo que el futuro drenador entregara al microservicio de notificaciones. */
    List<NotificacionPendiente> findByEnviadaEnIsNullOrderByCreadaEnAsc();
}
