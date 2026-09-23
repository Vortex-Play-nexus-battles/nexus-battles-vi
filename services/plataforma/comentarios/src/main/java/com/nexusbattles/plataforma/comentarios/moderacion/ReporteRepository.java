package com.nexusbattles.plataforma.comentarios.moderacion;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Los reportes de RF-COM-006 y lo que la cola de RF-COM-005 necesita contar. */
public interface ReporteRepository extends JpaRepository<RegistroDeReporte, String> {

    /** "Reporte duplicado del mismo usuario" es una de las excepciones de CA-03. */
    boolean existsByComentarioIdAndReportanteId(String comentarioId, String reportanteId);

    /** De la mas antigua a la mas reciente: el primer reporte desempata la cola. */
    List<RegistroDeReporte> findByComentarioIdOrderByFechaAsc(String comentarioId);

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
}
