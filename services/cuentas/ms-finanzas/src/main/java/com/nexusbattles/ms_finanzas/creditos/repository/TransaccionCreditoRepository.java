package com.nexusbattles.ms_finanzas.creditos.repository;

import com.nexusbattles.ms_finanzas.creditos.domain.TransaccionCredito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransaccionCreditoRepository extends JpaRepository<TransaccionCredito, UUID> {
    Optional<TransaccionCredito> findByRefId(String refId);
}
