package com.nexusbattles.ms_finanzas.creditos.repository;

import com.nexusbattles.ms_finanzas.creditos.domain.CuentaCredito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;

@Repository
public interface CuentaCreditoRepository extends JpaRepository<CuentaCredito, String> {

    // Búsqueda de solo lectura para endpoints como consultar saldo (evita error con @Transactional(readOnly = true))
    Optional<CuentaCredito> findByJugadorUid(String jugadorUid);

    // Lock pesimista exclusivo para operaciones de escritura concurrente (reservas, débitos, créditos)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CuentaCredito> findByJugadorUidWithLock(String jugadorUid);
}
