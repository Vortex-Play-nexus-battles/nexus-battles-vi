package com.nexusbattles.ms_finanzas.creditos.repository;

import com.nexusbattles.ms_finanzas.creditos.domain.ReservaCredito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReservaCreditoRepository extends JpaRepository<ReservaCredito, UUID> {

    Optional<ReservaCredito> findByIdempotencyKey(String idempotencyKey);
}
