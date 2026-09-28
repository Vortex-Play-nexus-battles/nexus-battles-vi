package com.nexusbattles.plataforma.torneos.torneo;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TorneoRepository extends JpaRepository<Torneo, UUID> {

    List<Torneo> findAllByOrderByCreadoEnDesc();

    /** El torneo mas reciente que cuenta para la ventana de 91 dias (los cancelados no cuentan). */
    Optional<Torneo> findFirstByEstadoNotAndCreadoEnAfterOrderByCreadoEnDesc(Torneo.Estado estado, OffsetDateTime desde);

    /**
     * El torneo con su fila bloqueada hasta el final de la transaccion
     * ({@code SELECT ... FOR UPDATE}).
     *
     * <p>Todo lo que cambia un torneo o sus equipos pasa por aqui (registrar un
     * equipo, inscribir, iniciar, cancelar, registrar un resultado): dos
     * peticiones sobre el mismo torneo se ordenan en vez de pisarse. Asi dos
     * inicios simultaneos no generan dos arboles ni dos cobros, el ultimo cupo
     * no se lo llevan dos equipos, y dos resultados a la vez no se sobrescriben
     * el arbol el uno al otro.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Torneo t where t.id = :id")
    Optional<Torneo> bloquear(@Param("id") UUID id);

    /**
     * Cerrojo de la ventana de 91 dias (RF-TOR-001) para toda la base, hasta
     * el final de la transaccion. Sin el, dos administradores creando a la vez
     * pasaban los dos la comprobacion y quedaban dos torneos en la ventana.
     */
    @Query(value = "select 1 from (select pg_advisory_xact_lock(9131)) as cerrojo", nativeQuery = true)
    Integer cerrojoDeLaVentana();
}
