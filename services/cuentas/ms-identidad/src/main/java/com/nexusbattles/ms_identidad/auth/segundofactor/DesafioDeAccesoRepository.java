package com.nexusbattles.ms_identidad.auth.segundofactor;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DesafioDeAccesoRepository extends JpaRepository<DesafioDeAcceso, UUID> {

    /**
     * El desafio por el resumen de su valor, bloqueado hasta el final de la
     * transaccion: dos canjes simultaneos del mismo desafio se ponen en fila y
     * el segundo lo encuentra ya usado.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DesafioDeAcceso d where d.tokenHash = :tokenHash")
    Optional<DesafioDeAcceso> bloquearPorResumen(@Param("tokenHash") String tokenHash);

    /** Limpieza al emitir uno nuevo: los caducados o usados de la cuenta ya no sirven para nada. */
    @Modifying(flushAutomatically = true)
    @Query("delete from DesafioDeAcceso d where d.usuarioId = :usuarioId"
            + " and (d.usadoEn is not null or d.expiraEn < :ahora)")
    int borrarGastados(@Param("usuarioId") Long usuarioId, @Param("ahora") LocalDateTime ahora);

    /** Al desactivar el segundo factor: los desafios a medias de la cuenta dejan de valer. */
    @Modifying(flushAutomatically = true)
    @Query("delete from DesafioDeAcceso d where d.usuarioId = :usuarioId")
    int borrarDe(@Param("usuarioId") Long usuarioId);
}
