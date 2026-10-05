package com.nexusbattles.ms_identidad.privacidad;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Solicitudes de cierre de cuenta (V6) y los borrados que ejecuta el derecho
 * al olvido.
 *
 * <p><b>Por que los borrados viven aqui</b> y no en el repositorio de cada
 * entidad: son UNA decision —que datos personales de ms-identidad se eliminan
 * al cerrar una cuenta— y leerlos juntos es la forma de revisarla. Todas las
 * tablas son de este mismo servicio (regla 7: ninguna base ajena).
 */
@Repository
public interface SolicitudDeCierreRepository extends JpaRepository<SolicitudDeCierre, UUID> {

    Optional<SolicitudDeCierre> findFirstByUsuarioUidAndEstadoOrderBySolicitadaEnDesc(UUID usuarioUid,
                                                                                      String estado);

    /** El cierre programado de la cuenta, si lo hay (como mucho uno: V6, indice unico parcial). */
    default Optional<SolicitudDeCierre> programadaDe(UUID usuarioUid) {
        return findFirstByUsuarioUidAndEstadoOrderBySolicitadaEnDesc(usuarioUid, SolicitudDeCierre.PROGRAMADA);
    }

    /** Las que la tarea programada ya puede ejecutar, de la mas antigua a la mas reciente. */
    @Query("select s.id from SolicitudDeCierre s where s.estado = :estado and s.programadaPara <= :ahora"
            + " order by s.programadaPara, s.id")
    List<UUID> vencidas(@Param("estado") String estado, @Param("ahora") LocalDateTime ahora, Pageable pagina);

    /** La solicitud bloqueada para escritura: dos ejecuciones a la vez no anonimizan dos veces. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SolicitudDeCierre s where s.id = :id")
    Optional<SolicitudDeCierre> bloquear(@Param("id") UUID id);

    // ---------------------------------------------------------- derecho al olvido

    /** Huellas de los dispositivos desde los que entro (RF-AUT-010): agente de usuario e IP resumidos. */
    @Modifying(flushAutomatically = true)
    @Query("delete from DispositivoConocido d where d.usuario.id = :usuarioId")
    int borrarDispositivosDe(@Param("usuarioId") Long usuarioId);

    /** Codigos de un solo uso (verificacion, recuperacion), usados o no. */
    @Modifying(flushAutomatically = true)
    @Query("delete from TokenCredencial t where t.usuario.id = :usuarioId")
    int borrarCodigosDe(@Param("usuarioId") Long usuarioId);

    /** El perfil entero: nombres, apellidos, avatar y preferencias. Comparte clave con la cuenta. */
    @Modifying(flushAutomatically = true)
    @Query("delete from PerfilUsuario p where p.id = :usuarioId")
    int borrarPerfilDe(@Param("usuarioId") Long usuarioId);
}
