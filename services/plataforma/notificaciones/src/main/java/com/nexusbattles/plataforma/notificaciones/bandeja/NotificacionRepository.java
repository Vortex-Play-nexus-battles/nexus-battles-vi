package com.nexusbattles.plataforma.notificaciones.bandeja;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificacionRepository extends JpaRepository<RegistroDeNotificacion, Long> {

    List<RegistroDeNotificacion> findByUsuarioIdOrderByCreadaEnAsc(String usuarioId);

    Optional<RegistroDeNotificacion> findByUsuarioIdAndAvisoId(String usuarioId, String avisoId);

    boolean existsByUsuarioIdAndAvisoId(String usuarioId, String avisoId);

    /**
     * Marca como leidos, en una sola sentencia, todos los avisos sin leer del
     * jugador (HU-NOT-001 CA-02). Solo toca las filas que estaban sin leer: una
     * llamada simultanea que llegue despues espera a esta y ya no las cuenta, y
     * repetirla devuelve 0.
     *
     * @return cuantas filas cambiaron
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RegistroDeNotificacion n set n.leida = true
             where n.usuarioId = :usuarioId and n.leida = false
            """)
    int marcarTodasLeidas(@Param("usuarioId") String usuarioId);

    long countByUsuarioIdAndLeidaFalse(String usuarioId);
}
