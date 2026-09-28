package com.nexusbattles.plataforma.torneos.torneo;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OperacionRepository extends JpaRepository<Operacion, UUID> {

    List<Operacion> findByTorneoIdOrderByCreadaEnAsc(UUID torneoId);

    List<Operacion> findByTorneoIdAndEstado(UUID torneoId, Operacion.Estado estado);

    boolean existsByClave(String clave);

    /**
     * Las que ya toca intentar: pendientes o reintentables con su hora
     * cumplida, y las que alguien reclamo y no termino antes de que venciera
     * su plazo (murio a mitad).
     */
    @Query("select o.id from Operacion o "
            + "where o.tipo in :tipos and ((o.estado in :porAtender and o.proximoIntento <= :ahora) "
            + "or (o.estado = :enCurso and o.bloqueadaHasta < :ahora)) "
            + "order by o.proximoIntento asc")
    List<UUID> porAtender(@Param("tipos") Collection<Operacion.Tipo> tipos,
                          @Param("porAtender") Collection<Operacion.Estado> porAtender,
                          @Param("enCurso") Operacion.Estado enCurso,
                          @Param("ahora") OffsetDateTime ahora,
                          Pageable pagina);

    /** Igual que {@link #porAtender} pero de un solo torneo. */
    @Query("select o.id from Operacion o "
            + "where o.torneoId = :torneoId and o.tipo in :tipos "
            + "and ((o.estado in :porAtender and o.proximoIntento <= :ahora) "
            + "or (o.estado = :enCurso and o.bloqueadaHasta < :ahora)) "
            + "order by o.proximoIntento asc")
    List<UUID> porAtenderDelTorneo(@Param("torneoId") UUID torneoId,
                                   @Param("tipos") Collection<Operacion.Tipo> tipos,
                                   @Param("porAtender") Collection<Operacion.Estado> porAtender,
                                   @Param("enCurso") Operacion.Estado enCurso,
                                   @Param("ahora") OffsetDateTime ahora,
                                   Pageable pagina);

    /**
     * Reclamar una operacion para ejecutarla: una sola sentencia condicional,
     * asi que de dos ejecuciones simultaneas (la peticion y la tarea
     * programada, o dos instancias) solo una cambia la fila y la otra recibe 0.
     * Si la que la reclamo muere, {@code bloqueadaHasta} vence y se puede
     * volver a reclamar.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Operacion o set o.estado = :enCurso, o.bloqueadaHasta = :hasta, "
            + "o.intentos = o.intentos + 1, o.actualizadaEn = :ahora "
            + "where o.id = :id and ((o.estado in :porAtender and o.proximoIntento <= :ahora) "
            + "or (o.estado = :enCurso and o.bloqueadaHasta < :ahora))")
    int reclamar(@Param("id") UUID id,
                 @Param("porAtender") Collection<Operacion.Estado> porAtender,
                 @Param("enCurso") Operacion.Estado enCurso,
                 @Param("ahora") OffsetDateTime ahora,
                 @Param("hasta") OffsetDateTime hasta);
}
