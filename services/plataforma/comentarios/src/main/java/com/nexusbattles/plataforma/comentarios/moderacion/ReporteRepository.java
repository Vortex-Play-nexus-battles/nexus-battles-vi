package com.nexusbattles.plataforma.comentarios.moderacion;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Los reportes de RF-COM-006 y lo que la cola de RF-COM-005 necesita contar. */
public interface ReporteRepository extends JpaRepository<RegistroDeReporte, String> {

    /** "Reporte duplicado del mismo usuario" es una de las excepciones de CA-03. */
    boolean existsByComentarioIdAndReportanteId(String comentarioId, String reportanteId);

    /** De la mas antigua a la mas reciente: el primer reporte desempata la cola. */
    List<RegistroDeReporte> findByComentarioIdOrderByFechaAsc(String comentarioId);

    /**
     * Los reportes de todos los comentarios de la cola en una consulta (B3):
     * la cola los pedia uno por uno, una consulta por comentario en revision.
     */
    List<RegistroDeReporte> findByComentarioIdInOrderByFechaAsc(Collection<String> comentarioIds);

    long countByComentarioId(String comentarioId);

    /**
     * Cuantos reportes lleva ese usuario desde un instante.
     *
     * <p>Es el limite que la ficha exige sin fijar su valor. Se cuenta sobre
     * una ventana movil y no sobre "el dia natural" a proposito: un limite por
     * dia natural se agota a las 23:59 y se renueva a las 00:00, que es una
     * invitacion a esperar en vez de una moderacion del comportamiento.
     */
    long countByReportanteIdAndFechaAfter(String reportanteId, Instant desde);

    /**
     * Los comentarios con algun reporte PENDIENTE: posterior a la ultima
     * decision que atiende reportes ({@link AccionDeModeracion#RESUELVEN_REPORTES}).
     *
     * <p>Contrato 1.8.0 (RF-COM-006 CA-01): un reporte encola el comentario sin
     * ocultarlo, asi que la cola ya no puede salir solo del estado EN_REVISION.
     * Lo pendiente se deriva de las fechas —reporte frente a asiento— y no se
     * guarda: no hay bandera que pueda quedar a medias entre dos tablas.
     */
    @Query("""
            select distinct r.comentarioId from RegistroDeReporte r
            where not exists (
                select a.id from AsientoDeModeracion a
                where a.comentarioId = r.comentarioId
                  and a.fecha >= r.fecha
                  and a.accion in :resolutivas)
            """)
    List<String> comentariosConReportesPendientes(
            @Param("resolutivas") Collection<AccionDeModeracion> resolutivas);

    /** Cuantos reportes de ese comentario siguen pendientes (ver arriba). */
    @Query("""
            select count(r) from RegistroDeReporte r
            where r.comentarioId = :comentarioId
              and not exists (
                select a.id from AsientoDeModeracion a
                where a.comentarioId = r.comentarioId
                  and a.fecha >= r.fecha
                  and a.accion in :resolutivas)
            """)
    long contarPendientes(@Param("comentarioId") String comentarioId,
            @Param("resolutivas") Collection<AccionDeModeracion> resolutivas);
}
