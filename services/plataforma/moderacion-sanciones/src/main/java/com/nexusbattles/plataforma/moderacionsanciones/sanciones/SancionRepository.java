package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface SancionRepository extends JpaRepository<Sancion, UUID> {

    List<Sancion> findByUsuarioIdOrderByEmitidaEnDesc(UUID usuarioId);

    /** Las que pueden restringir: no revertidas y distintas de advertencia. */
    List<Sancion> findByUsuarioIdAndRevertidaEnIsNullAndTipoNot(UUID usuarioId, Sancion.Tipo tipo);

    /** Las emitidas en un periodo, para las metricas de moderacion (HU-MET-001). */
    List<Sancion> findByEmitidaEnBetweenOrderByEmitidaEnAsc(OffsetDateTime desde, OffsetDateTime hasta);

    /** Un usuario con cuantas sanciones efectivas tiene y cuando recibio la ultima. */
    interface Reincidente {
        UUID getUsuarioId();

        long getSanciones();

        OffsetDateTime getUltimaEn();
    }

    /**
     * Quienes tienen {@code minimo} sanciones <b>no revertidas</b> o mas, de
     * cualquier tipo, las mas sancionadas primero (HU-USR-008, D-45). Una
     * sancion revertida por apelacion o levantada ya no cuenta.
     */
    @Query("select s.usuarioId as usuarioId, count(s) as sanciones, max(s.emitidaEn) as ultimaEn "
            + "from Sancion s where s.revertidaEn is null group by s.usuarioId having count(s) >= :minimo "
            + "order by count(s) desc, max(s.emitidaEn) desc")
    List<Reincidente> reincidentes(@Param("minimo") long minimo, Pageable pagina);

    /** Cuantos usuarios cumplen {@link #reincidentes}: el total, aunque la pagina sea mas corta. */
    @Query("select count(distinct s.usuarioId) from Sancion s where s.revertidaEn is null and "
            + "(select count(x) from Sancion x where x.usuarioId = s.usuarioId and x.revertidaEn is null) >= :minimo")
    long contarReincidentes(@Param("minimo") long minimo);
}
