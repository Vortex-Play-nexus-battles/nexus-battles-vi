package com.nexusbattles.ms_subastas.subastas.repository;

import com.nexusbattles.ms_subastas.subastas.model.PublicacionIdempotente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface PublicacionIdempotenteRepository extends JpaRepository<PublicacionIdempotente, String> {

    /**
     * Adquiere la clave si nadie la tiene. ON CONFLICT y no un save que falle:
     * el choque abortaria la transaccion, y lo que se quiere es saber que ya
     * existia para leerla.
     *
     * @return 1 si la adquirio esta llamada, 0 si ya existia
     */
    @Modifying
    @Query(value = """
            insert into publicaciones_idempotentes
                (clave, huella, titular, estado, creada_en, actualizada_en)
            values (:clave, :huella, :titular, 'EN_CURSO', :ahora, :ahora)
            on conflict (clave) do nothing
            """, nativeQuery = true)
    int insertarSiNoExiste(@Param("clave") String clave, @Param("huella") String huella,
                           @Param("titular") UUID titular, @Param("ahora") Instant ahora);
}
