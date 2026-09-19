package com.nexusbattles.ms_finanzas.transacciones;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransaccionRepository extends JpaRepository<Transaccion, UUID> {

    Optional<Transaccion> findByRefId(String refId);

    boolean existsByRefId(String refId);

    Page<Transaccion> findByUidUsuarioOrderByCreadoDesc(String uidUsuario, Pageable pagina);
}
