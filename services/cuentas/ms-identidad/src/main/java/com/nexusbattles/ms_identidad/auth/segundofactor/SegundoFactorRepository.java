package com.nexusbattles.ms_identidad.auth.segundofactor;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SegundoFactorRepository extends JpaRepository<SegundoFactor, Long> {

    /**
     * La fila bloqueada hasta el final de la transaccion: dos confirmaciones
     * del mismo enrolamiento a la vez no generan dos juegos de codigos de
     * recuperacion.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SegundoFactor s where s.usuarioId = :usuarioId")
    Optional<SegundoFactor> bloquear(@Param("usuarioId") Long usuarioId);

    /** Si la cuenta tiene un segundo factor confirmado (el login lo pide). */
    boolean existsByUsuarioIdAndActivoTrue(Long usuarioId);

    /**
     * Anota el paso TOTP recien aceptado, solo si es posterior al ultimo. Es un
     * UPDATE condicionado, no «leer, comparar, guardar»: de dos peticiones con
     * el mismo codigo a la vez, una encuentra la fila con el paso ya anotado y
     * actualiza cero filas. 0 = el codigo ya se uso.
     */
    @Modifying(flushAutomatically = true)
    @Query("update SegundoFactor s set s.ultimoPasoUsado = :paso where s.usuarioId = :usuarioId"
            + " and s.activo = true and (s.ultimoPasoUsado is null or s.ultimoPasoUsado < :paso)")
    int registrarPaso(@Param("usuarioId") Long usuarioId, @Param("paso") long paso);

    @Modifying(flushAutomatically = true)
    @Query("delete from SegundoFactor s where s.usuarioId = :usuarioId")
    int borrarDe(@Param("usuarioId") Long usuarioId);
}
