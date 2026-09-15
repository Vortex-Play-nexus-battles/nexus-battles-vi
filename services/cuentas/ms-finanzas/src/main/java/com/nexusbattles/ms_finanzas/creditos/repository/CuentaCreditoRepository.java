package com.nexusbattles.ms_finanzas.creditos.repository;

import com.nexusbattles.ms_finanzas.creditos.domain.CuentaCredito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;

@Repository
public interface CuentaCreditoRepository extends JpaRepository<CuentaCredito, String> {

    // Lock pesimista para evitar condiciones de carrera en operaciones concurrentes de créditos
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CuentaCredito> findByJugadorUid(String jugadorUid);
}
