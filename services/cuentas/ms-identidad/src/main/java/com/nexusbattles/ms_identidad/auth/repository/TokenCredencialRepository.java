package com.nexusbattles.ms_identidad.auth.repository;

import com.nexusbattles.ms_identidad.auth.model.TokenCredencial;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

/**
 * Codigos de un solo uso (B1). Desde B1 ningun metodo busca por el valor del
 * codigo: el valor no esta en la base, solo su resumen. Se busca siempre el
 * ULTIMO codigo de una familia de una cuenta y se compara con BCrypt.
 */
@Repository
public interface TokenCredencialRepository extends JpaRepository<TokenCredencial, Long> {

    /**
     * El ultimo codigo emitido de esos tipos para esa cuenta, BLOQUEADO para
     * escritura: dos intentos simultaneos contra el mismo codigo se
     * serializan, asi que el tope de intentos es exacto y no «mas o menos
     * cinco» con peticiones en paralelo.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TokenCredencial> findFirstByUsuario_IdAndTipoInOrderByIdDesc(Long usuarioId, Collection<String> tipos);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TokenCredencial t where t.id = :id")
    Optional<TokenCredencial> bloquear(@Param("id") Long id);

    /** Al emitir uno nuevo, los anteriores de la familia dejan de valer. */
    @Modifying(flushAutomatically = true)
    @Query("update TokenCredencial t set t.anuladoEn = :ahora"
            + " where t.usuario.id = :usuarioId and t.tipo in :tipos"
            + " and t.usado = false and t.anuladoEn is null")
    int anularVigentes(@Param("usuarioId") Long usuarioId, @Param("tipos") Collection<String> tipos,
                       @Param("ahora") LocalDateTime ahora);

    /**
     * Canje: solo gana quien lo marca primero. Dos confirmaciones del mismo
     * codigo a la vez: la segunda se queda esperando el bloqueo de la fila y,
     * cuando la primera confirma, ve {@code usado = true} y actualiza cero filas.
     */
    @Modifying(flushAutomatically = true)
    @Query("update TokenCredencial t set t.usado = true, t.usadoEn = :ahora"
            + " where t.id = :id and t.usado = false and t.anuladoEn is null")
    int marcarUsado(@Param("id") Long id, @Param("ahora") LocalDateTime ahora);

    /** Limite «N por hora», calculado en la base (este servicio no tiene Redis). */
    @Query("select count(t) from TokenCredencial t"
            + " where t.usuario.id = :usuarioId and t.tipo = :tipo and t.creadoEn > :desde")
    long contarEmitidosDesde(@Param("usuarioId") Long usuarioId, @Param("tipo") String tipo,
                             @Param("desde") LocalDateTime desde);

    /** Limite «un minuto entre dos»: cuando se emitio el ultimo de ese tipo. */
    @Query("select max(t.creadoEn) from TokenCredencial t where t.usuario.id = :usuarioId and t.tipo = :tipo")
    Optional<LocalDateTime> ultimaEmision(@Param("usuarioId") Long usuarioId, @Param("tipo") String tipo);

    boolean existsByUsuario_IdAndTipo(Long usuarioId, String tipo);

    boolean existsByUsuario_IdAndTipoAndUsadoTrue(Long usuarioId, String tipo);
}
