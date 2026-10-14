package com.nexusbattles.ms_chatbot.chat.repository;

import com.nexusbattles.ms_chatbot.chat.model.BrechaConocimiento;
import com.nexusbattles.ms_chatbot.chat.model.EstadoBrecha;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface BrechaConocimientoRepository extends JpaRepository<BrechaConocimiento, UUID> {

    // Clave de agrupacion de HU-CHA-011: si ya existe una brecha para esta
    // pregunta normalizada, se incrementa su contador en vez de crear otra.
    Optional<BrechaConocimiento> findByTextoNormalizado(String textoNormalizado);

    // ms-chatbot.yaml 1.7.0 (GET /chatbot/admin/brechas): las brechas con
    // actividad en [desde, hasta) (preguntadas por ultima vez desde el inicio
    // y por primera vez antes del fin), las de mas escalamientos + «no util»
    // primero; a igual total, la mas reciente, y el id para que el orden no
    // cambie entre paginas.
    @Query(value = """
        select b from BrechaConocimiento b
        where b.fechaUltimaOcurrencia >= :desde and b.fechaPrimeraOcurrencia < :hasta
        order by (b.contadorEscalamiento + b.contadorNoUtil) desc, b.fechaUltimaOcurrencia desc, b.id asc
        """,
        countQuery = """
        select count(b) from BrechaConocimiento b
        where b.fechaUltimaOcurrencia >= :desde and b.fechaPrimeraOcurrencia < :hasta
        """)
    Page<BrechaConocimiento> buscarConActividadEntre(@Param("desde") Instant desde, @Param("hasta") Instant hasta,
                                                     Pageable pagina);

    // Lo mismo, solo las de un estado.
    @Query(value = """
        select b from BrechaConocimiento b
        where b.estado = :estado
          and b.fechaUltimaOcurrencia >= :desde and b.fechaPrimeraOcurrencia < :hasta
        order by (b.contadorEscalamiento + b.contadorNoUtil) desc, b.fechaUltimaOcurrencia desc, b.id asc
        """,
        countQuery = """
        select count(b) from BrechaConocimiento b
        where b.estado = :estado
          and b.fechaUltimaOcurrencia >= :desde and b.fechaPrimeraOcurrencia < :hasta
        """)
    Page<BrechaConocimiento> buscarConActividadEntre(@Param("estado") EstadoBrecha estado,
                                                     @Param("desde") Instant desde, @Param("hasta") Instant hasta,
                                                     Pageable pagina);
}
