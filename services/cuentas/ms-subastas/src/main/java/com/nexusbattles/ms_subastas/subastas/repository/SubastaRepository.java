package com.nexusbattles.ms_subastas.subastas.repository;

import com.nexusbattles.ms_subastas.subastas.model.EstadoSubasta;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubastaRepository extends JpaRepository<Subasta, UUID>,
    JpaSpecificationExecutor<Subasta> {

    /**
     * Lee la subasta con lock pesimista (SELECT ... FOR UPDATE). Es el guardia
     * real de concurrencia de HU-SUB-004: serializa las pujas sobre la misma
     * subasta entre procesos y replicas, donde un synchronized de la JVM no
     * alcanza. Solo valido dentro de una transaccion.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Subasta s where s.id = :id")
    Optional<Subasta> findByIdParaActualizar(@Param("id") UUID id);

    /**
     * Subastas que siguen ACTIVA pero cuyo plazo ya paso. Devuelve solo ids a
     * proposito: cada una se cierra en su propia transaccion, que vuelve a
     * leerla con lock, de modo que una subasta problematica no arrastre al resto.
     */
    @Query("select s.id from Subasta s where s.estado = 'ACTIVA' and s.fechaFin <= :ahora")
    List<UUID> findIdsDeActivasVencidas(@Param("ahora") Instant ahora);

    /**
     * Subastas activas con al menos una puja automatica lista para reaccionar:
     * activa, de un jugador que no es ya el mejor postor. Es la entrada del
     * motor de pujas automaticas.
     */
    @Query("""
            select distinct s.id from Subasta s, PujaAutomatica pa
            where pa.subastaId = s.id
              and pa.activa = true
              and s.estado = 'ACTIVA'
              and s.fechaFin > :ahora
              and (s.mejorPostorId is null or s.mejorPostorId <> pa.jugadorId)
            """)
    List<UUID> findIdsConPujaAutomaticaPendiente(@Param("ahora") Instant ahora);

    // --- HU-SUB-011 (Cristian): busqueda paginada con filtros dinamicos.
    // findAll(Specification, Pageable) llega gratis con JpaSpecificationExecutor,
    // no se declara aqui -- ver SubastaSpecifications para los filtros.

    /**
     * HU-SUB-011: autocompletado. Devuelve solo los nombres, ya ordenados por
     * popularidad (mas pujas primero).
     *
     * NO usa SELECT DISTINCT nombreProducto: Postgres exige que toda columna
     * del ORDER BY aparezca en el SELECT cuando hay DISTINCT (SQLState 42P10),
     * y cantidadPujas no es parte de lo que queremos seleccionar. En su lugar
     * se seleccionan las entidades completas (sin DISTINCT, ya ordenadas), y
     * el servicio deduplica los nombres en Java preservando ese orden.
     */
    @Query("""
            select s from Subasta s
            where s.estado = 'ACTIVA'
              and lower(s.nombreProducto) like lower(concat('%', :texto, '%'))
            order by s.cantidadPujas desc
            """)
    List<Subasta> buscarSugeridasPorTexto(@Param("texto") String texto, Pageable limite);

    // --- B8 (7.7 del documento del curso) ------------------------------------

    /**
     * Candado de publicacion por vendedor, hasta el final de la transaccion
     * ({@code pg_advisory_xact_lock}).
     *
     * <p>El tope de 10 subastas activas (7.7.10) se comprueba contando; sin
     * candado, dos publicaciones simultaneas del mismo vendedor con 9 activas
     * contarian 9 las dos y quedarian 11. Un {@code SELECT ... FOR UPDATE} de
     * sus subastas no sirve: con cero filas no bloquea nada, y la fila nueva
     * que inserta el otro no la ve. El candado consultivo es del vendedor, no
     * de una fila, asi que si serializa. Se envuelve en un SELECT que devuelve
     * 1 porque la funcion devuelve {@code void}.
     */
    @Query(value = "select 1 from (select pg_advisory_xact_lock(:clave)) as candado", nativeQuery = true)
    Integer bloquearPublicacionesDe(@Param("clave") long clave);

    long countByVendedorIdAndEstado(UUID vendedorId, EstadoSubasta estado);

    /** «Mis subastas» (7.7.9), de la mas reciente a la mas antigua. */
    List<Subasta> findByVendedorIdOrderByFechaFinDesc(UUID vendedorId);

    List<Subasta> findByVendedorIdAndEstadoOrderByFechaFinDesc(UUID vendedorId, EstadoSubasta estado);

    /**
     * Activas que cierran en la proxima hora y todavia no avisaron (7.7.8,
     * «Aviso 1 hora antes de finalizar»). Solo ids: cada una se avisa en su
     * propia transaccion.
     */
    @Query("""
            select s.id from Subasta s
            where s.estado = com.nexusbattles.ms_subastas.subastas.model.EstadoSubasta.ACTIVA
              and s.recordatorioEnviadoEn is null
              and s.fechaFin > :ahora
              and s.fechaFin <= :limite
            """)
    List<UUID> idsParaRecordar(@Param("ahora") Instant ahora, @Param("limite") Instant limite);

    /**
     * Subastas terminadas del vendedor por estado: la base de su reputacion
     * (7.7.9, «Calificacion del vendedor basada en transacciones previas»).
     * Filas de {estado, cantidad}.
     */
    @Query("""
            select s.estado, count(s) from Subasta s
            where s.vendedorId = :vendedorId
              and s.estado <> com.nexusbattles.ms_subastas.subastas.model.EstadoSubasta.ACTIVA
            group by s.estado
            """)
    List<Object[]> terminadasPorEstado(@Param("vendedorId") UUID vendedorId);
}
