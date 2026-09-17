package com.nexusbattles.ms_finanzas.creditos.repository;

import com.nexusbattles.ms_finanzas.creditos.domain.CuentaCredito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

@Repository
public interface CuentaCreditoRepository extends JpaRepository<CuentaCredito, String> {

    // Lock pesimista para evitar condiciones de carrera en operaciones concurrentes de escritura
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CuentaCredito> findByJugadorUid(String jugadorUid);

    // Búsqueda de solo lectura para endpoints como consultar saldo (evita error 500)
    @Query("SELECT c FROM CuentaCredito c WHERE c.jugadorUid = :jugadorUid")
    Optional<CuentaCredito> findByJugadorUidReadOnly(@Param("jugadorUid") String jugadorUid);
}
