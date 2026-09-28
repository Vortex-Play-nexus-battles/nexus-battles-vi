package com.nexusbattles.ms_subastas.notificaciones;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificacionPendienteRepository extends JpaRepository<NotificacionPendiente, UUID> {

    /**
     * Lo que el drenador tiene que entregar a la bandeja, en orden de llegada.
     * Las rechazadas para siempre quedan fuera: antes una sola bloqueaba la
     * cola entera, porque el drenador corta el lote en el primer fallo.
     */
    List<NotificacionPendiente> findByEnviadaEnIsNullAndFallidaEnIsNullOrderByCreadaEnAsc();

    /** Los correos pendientes cuyo proximo intento ya llego, los mas antiguos primero. */
    @Query("""
            select n from NotificacionPendiente n
            where n.conCorreo = true
              and n.correoEstado = com.nexusbattles.ms_subastas.notificaciones.EstadoCorreo.PENDIENTE
              and (n.correoProximoIntentoEn is null or n.correoProximoIntentoEn <= :ahora)
            order by n.creadaEn asc
            """)
    List<NotificacionPendiente> correosPorEnviar(@Param("ahora") Instant ahora, Pageable lote);

    /**
     * Encola un aviso si ese mismo evento no estaba ya encolado.
     *
     * <p>{@code ON CONFLICT DO NOTHING} y no un {@code save} que falle: en
     * PostgreSQL una violacion de unicidad aborta la transaccion entera, y esta
     * es la transaccion del negocio (la puja, el cierre). Capturar la
     * excepcion no la salvaria.
     *
     * <p>Los textos opcionales llegan como cadena vacia y se vuelven NULL con
     * {@code nullif}: un parametro nulo en una consulta nativa puede ligarse
     * sin tipo, y PostgreSQL lo rechaza al compararlo con la columna.
     *
     * @return 1 si se encolo, 0 si ya existia
     */
    @Modifying
    @Transactional
    @Query(value = """
            insert into notificaciones_pendientes
                (id, tipo, destinatario_id, subasta_id, titulo, detalle, creada_en,
                 con_correo, asunto, correo_estado, correo_intentos)
            values
                (:id, :tipo, :destinatario, :subasta, nullif(:titulo, ''), nullif(:detalle, ''), :creadaEn,
                 :conCorreo, nullif(:asunto, ''), nullif(:correoEstado, ''), 0)
            on conflict (id) do nothing
            """, nativeQuery = true)
    int encolarSiNoExiste(@Param("id") UUID id,
                          @Param("tipo") String tipo,
                          @Param("destinatario") UUID destinatario,
                          @Param("subasta") UUID subasta,
                          @Param("titulo") String titulo,
                          @Param("detalle") String detalle,
                          @Param("creadaEn") Instant creadaEn,
                          @Param("conCorreo") boolean conCorreo,
                          @Param("asunto") String asunto,
                          @Param("correoEstado") String correoEstado);
}
