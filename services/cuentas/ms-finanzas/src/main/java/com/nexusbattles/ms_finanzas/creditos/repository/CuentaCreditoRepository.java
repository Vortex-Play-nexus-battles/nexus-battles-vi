package com.nexusbattles.ms_finanzas.creditos.repository;

import com.nexusbattles.ms_finanzas.creditos.domain.CuentaCredito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;

@Repository
public interface CuentaCreditoRepository extends JpaRepository<CuentaCredito, String> {

    // Lock pesimista para evitar condiciones de carrera entre operaciones
    // concurrentes sobre la misma cuenta (reservar, debitar, acreditar).
    //
    // Se mantiene UN SOLO metodo, siempre con bloqueo: la alternativa que
    // llego en el merge partia esto en dos y anadia findByJugadorUidWithLock,
    // que Spring Data no puede derivar (no existe la propiedad withLock) y
    // que ademas no usaba nadie. Los metodos de lectura resuelven el problema
    // del readOnly quitando ese flag, no quitando el bloqueo.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CuentaCredito> findByJugadorUid(String jugadorUid);
}
