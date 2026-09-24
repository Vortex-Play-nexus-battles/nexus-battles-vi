package com.nexusbattles.ms_chatbot.chat.motor.repository;

import com.nexusbattles.ms_chatbot.chat.motor.model.EstadoVersion;
import com.nexusbattles.ms_chatbot.chat.motor.model.VersionBaseConocimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VersionBaseConocimientoRepository extends JpaRepository<VersionBaseConocimiento, UUID> {

    // Solo para BORRADOR y PRODUCCION, que los indices parciales de V4
    // garantizan unicos. RETIRADA puede tener varias.
    Optional<VersionBaseConocimiento> findByEstado(EstadoVersion estado);

    List<VersionBaseConocimiento> findAllByOrderByNumeroDesc();

    @Query("select coalesce(max(v.numero), 0) from VersionBaseConocimiento v")
    int buscarNumeroMaximo();

    // HU-CHA-012 (revertir): la retirada que estuvo en produccion justo antes
    // de la actual es la que entro a produccion mas recientemente.
    Optional<VersionBaseConocimiento> findFirstByEstadoOrderByFechaDespliegueDesc(EstadoVersion estado);
}
