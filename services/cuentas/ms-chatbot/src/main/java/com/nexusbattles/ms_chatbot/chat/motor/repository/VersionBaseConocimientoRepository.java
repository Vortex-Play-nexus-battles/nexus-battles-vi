package com.nexusbattles.ms_chatbot.chat.motor.repository;

import com.nexusbattles.ms_chatbot.chat.motor.model.EstadoVersion;
import com.nexusbattles.ms_chatbot.chat.motor.model.VersionBaseConocimiento;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VersionBaseConocimientoRepository extends JpaRepository<VersionBaseConocimiento, UUID> {

    // Solo para BORRADOR y PRODUCCION, que los indices parciales de V4
    // garantizan unicos. RETIRADA puede tener varias.
    Optional<VersionBaseConocimiento> findByEstado(EstadoVersion estado);

    // ms-chatbot.yaml 1.6.0: la version en ese estado (BORRADOR o PRODUCCION)
    // con su fila bloqueada hasta el final de la transaccion (en PostgreSQL,
    // SELECT ... FOR NO KEY UPDATE). Es lo PRIMERO que lee cada transicion:
    // la siguiente espera aqui a que la anterior termine. Si mientras esperaba
    // la fila dejo de estar en ese estado, PostgreSQL ya no la devuelve (ver
    // conocimiento.BloqueoDeVersiones). No sirve en una transaccion de solo
    // lectura: PostgreSQL no bloquea filas en ellas.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VersionBaseConocimiento v where v.estado = :estado")
    Optional<VersionBaseConocimiento> bloquearLaDeEstado(@Param("estado") EstadoVersion estado);

    // 1.6.0: si hay una version en ese estado, sin cargarla ni bloquearla.
    boolean existsByEstado(EstadoVersion estado);

    // 7.4.9 (ms-chatbot.yaml 1.9.0): solo el id de la version en ese estado
    // (una fila por el indice parcial de V4). Es la consulta barata que se
    // hace en cada mensaje para saber si la base en memoria sigue vigente:
    // un despliegue o una reversion, hechos en esta instancia o en otra,
    // cambian este id y lo guardado de la anterior deja de valer.
    @Query("select v.id from VersionBaseConocimiento v where v.estado = :estado")
    Optional<UUID> buscarIdDeLaDeEstado(@Param("estado") EstadoVersion estado);

    // 1.6.0: si la candidata tiene una publicacion programada que ya vencio.
    // La tarea de cada minuto pregunta esto sin bloquear nada: casi siempre la
    // respuesta es no.
    boolean existsByEstadoAndDespliegueProgramadoEnLessThanEqual(EstadoVersion estado, Instant ahora);

    List<VersionBaseConocimiento> findAllByOrderByNumeroDesc();

    @Query("select coalesce(max(v.numero), 0) from VersionBaseConocimiento v")
    int buscarNumeroMaximo();

    // HU-CHA-012 (revertir): la retirada que estuvo en produccion justo antes
    // de la actual es la que entro a produccion mas recientemente.
    Optional<VersionBaseConocimiento> findFirstByEstadoOrderByFechaDespliegueDesc(EstadoVersion estado);
}
