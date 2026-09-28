package com.nexusbattles.ms_subastas.pujas.repository;

import com.nexusbattles.ms_subastas.pujas.model.EstadoPuja;
import com.nexusbattles.ms_subastas.pujas.model.Puja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PujaRepository extends JpaRepository<Puja, UUID> {

    /** La puja que hoy es la oferta vigente de la subasta. */
    /**
     * Busca la puja que ya creo una peticion con esta misma Idempotency-Key.
     * Es lo que convierte un reintento en una respuesta repetida en vez de en
     * una puja nueva. Se apoya en el unico parcial uq_pujas_idempotency_key.
     */
    Optional<Puja> findByIdempotencyKey(String idempotencyKey);

    Optional<Puja> findBySubastaIdAndEstado(UUID subastaId, EstadoPuja estado);

    /** Cuantas pujas del jugador siguen siendo oferta vigente (tope de 50). */
    int countByJugadorIdAndEstado(UUID jugadorId, EstadoPuja estado);

    /** Su ultima puja en cualquier subasta, para validar el intervalo minimo de 5 s. */
    /**
     * Ultima puja del jugador EN ESA SUBASTA, para el intervalo minimo de 5 s.
     *
     * <p>Tiene que filtrar por subasta. La version global (solo por jugadorId)
     * bloqueaba al jugador en todas las demas subastas durante 5 s: con las 10
     * simultaneas que la propia HU permite, solo alcanzaba a pujar en una cada
     * 5 s, y su propia puja automatica en una subasta le impedia pujar a mano
     * en otra. El freno es contra el spam dentro de una subasta, no contra
     * participar en varias.
     */
    Optional<Puja> findFirstByJugadorIdAndSubastaIdOrderByCreadaEnDesc(UUID jugadorId, UUID subastaId);

    /**
     * Todos los jugadores que pujaron en la subasta, ganando o no. Es la lista
     * de destinatarios de "notificando a quienes hubieran pujado" del criterio 2.
     */
    /** El historial de la subasta, de la mas reciente a la mas antigua. */
    List<Puja> findBySubastaIdOrderByCreadaEnDesc(UUID subastaId);

    /** Lo que el jugador tiene retenido ahora mismo: sus pujas que siguen siendo oferta vigente. */
    @Query("select coalesce(sum(p.monto), 0) from Puja p where p.jugadorId = :jugadorId and p.estado = :estado")
    BigDecimal sumarMontoPorJugadorYEstado(@Param("jugadorId") UUID jugadorId, @Param("estado") EstadoPuja estado);

    @Query("select distinct p.jugadorId from Puja p where p.subastaId = :subastaId")
    List<UUID> findDistinctJugadorIdBySubastaId(@Param("subastaId") UUID subastaId);

    /**
     * Si la subasta tiene alguna puja registrada. Es la condicion de 7.7.10
     * para poder cancelarla («Posible solo si no hay pujas registradas»); se
     * pregunta a la tabla y no al contador de la subasta, que las filas
     * sembradas a mano pueden no tener al dia.
     */
    boolean existsBySubastaId(UUID subastaId);

    /** Sus pujas en un estado: las GANADORAS son sus compras (historial, 7.7.9). */
    List<Puja> findByJugadorIdAndEstado(UUID jugadorId, EstadoPuja estado);

    /**
     * «Mis pujas» (7.7.9): por cada subasta en la que pujo, su mejor oferta y
     * cuando pujo por ultima vez. Filas de {subastaId, max(monto), max(creadaEn)}.
     */
    @Query("select p.subastaId, max(p.monto), max(p.creadaEn) from Puja p where p.jugadorId = :jugadorId "
            + "group by p.subastaId")
    List<Object[]> resumenPorSubastaDe(@Param("jugadorId") UUID jugadorId);
}
