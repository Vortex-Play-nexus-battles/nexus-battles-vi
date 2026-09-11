package com.nexusbattles.ms_subastas.subastas.repository;

import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SubastaRepository extends JpaRepository<Subasta, UUID> {

    /**
     * Lee la subasta con lock pesimista (SELECT ... FOR UPDATE). Es el guardia
     * real de concurrencia de HU-SUB-004: serializa las pujas sobre la misma
     * subasta entre procesos y replicas, donde un synchronized de la JVM no
     * alcanza. Solo valido dentro de una transaccion.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Subasta s where s.id = :id")
    Optional<Subasta> findByIdParaActualizar(@Param("id") UUID id);
}
