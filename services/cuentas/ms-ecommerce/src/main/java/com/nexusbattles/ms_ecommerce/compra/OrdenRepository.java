package com.nexusbattles.ms_ecommerce.compra;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrdenRepository extends JpaRepository<Orden, UUID> {

    Optional<Orden> findByUsuarioIdAndClaveIdempotencia(String usuarioId, String claveIdempotencia);

    /** Las ordenes del jugador, de la mas reciente a la mas antigua (GET /ordenes). */
    List<Orden> findByUsuarioIdOrderByCreadaEnDesc(String usuarioId);

    /** Una orden solo si es del jugador: la de otro no existe para el (404, nunca 403). */
    Optional<Orden> findByIdAndUsuarioId(UUID id, String usuarioId);

    /**
     * Toma la concesion de la orden: solo si nadie la tiene o la que habia
     * caduco. Es la unica forma de procesar una orden, y es atomica: dos
     * procesos (la peticion y la tarea programada, o dos reintentos con la
     * misma clave) no pueden tenerla a la vez.
     *
     * <p>Sube la version: si quien tenia una concesion caducada intenta
     * guardar despues, su escritura falla por bloqueo optimista en vez de
     * pisar a quien la tiene ahora.
     *
     * @return 1 si se tomo, 0 si la tiene otro
     */
    @Modifying
    @Transactional
    @Query("""
            update Orden o set o.bloqueadaHasta = :hasta, o.version = o.version + 1
             where o.id = :id and (o.bloqueadaHasta is null or o.bloqueadaHasta < :ahora)""")
    int tomar(@Param("id") UUID id, @Param("ahora") Instant ahora, @Param("hasta") Instant hasta);

    /** Renueva la concesion de quien ya la tiene, entre dos pasos largos. */
    @Modifying
    @Transactional
    @Query("update Orden o set o.bloqueadaHasta = :hasta where o.id = :id and o.bloqueadaHasta is not null")
    int renovar(@Param("id") UUID id, @Param("hasta") Instant hasta);

    /** Suelta la concesion: la orden queda libre para el siguiente que la retome. */
    @Modifying
    @Transactional
    @Query("update Orden o set o.bloqueadaHasta = null, o.version = o.version + 1 where o.id = :id")
    int soltar(@Param("id") UUID id);

    /** Si el jugador tiene ahora mismo una compra en proceso (con la concesion tomada). */
    @Query("select count(o) > 0 from Orden o where o.usuarioId = :usuarioId and o.bloqueadaHasta > :ahora")
    boolean hayCompraEnCurso(@Param("usuarioId") String usuarioId, @Param("ahora") Instant ahora);

    /**
     * Las ordenes a medias a las que ya les toca reintento y que nadie tiene:
     * cobradas sin entregar, entregadas sin asiento o sin correo, por
     * compensar, o rechazadas cuyo asiento no se pudo escribir.
     */
    @Query("""
            select o.id from Orden o
             where (o.estado in :enCurso or (o.estado = :rechazada and o.asiento = :asientoPendiente))
               and (o.proximoIntentoEn is null or o.proximoIntentoEn <= :ahora)
               and (o.bloqueadaHasta is null or o.bloqueadaHasta < :ahora)
             order by o.proximoIntentoEn asc""")
    List<UUID> porRetomar(@Param("enCurso") Collection<EstadoOrden> enCurso,
                          @Param("rechazada") EstadoOrden rechazada,
                          @Param("asientoPendiente") EstadoDelAsiento asientoPendiente,
                          @Param("ahora") Instant ahora,
                          Pageable lote);

    /** Ordenes PENDIENTE que nadie reintento a tiempo (la pasarela no respondio). */
    @Query("""
            select o.id from Orden o
             where o.estado = :pendiente and o.creadaEn < :limite
               and (o.bloqueadaHasta is null or o.bloqueadaHasta < :ahora)""")
    List<UUID> pendientesCaducadas(@Param("pendiente") EstadoOrden pendiente, @Param("limite") Instant limite,
                                   @Param("ahora") Instant ahora, Pageable lote);

    /** Anota que la linea ya tiene reservadas tantas unidades (nunca hacia atras). */
    @Modifying
    @Transactional
    @Query("""
            update LineaDeOrden l set l.unidadesReservadas = :unidades
             where l.id = :linea and l.unidadesReservadas < :unidades""")
    int anotarReserva(@Param("linea") Long linea, @Param("unidades") int unidades);
}
