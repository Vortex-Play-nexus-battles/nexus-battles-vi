package com.nexusbattles.ms_chatbot.chat.motor.repository;

import com.nexusbattles.ms_chatbot.chat.motor.model.EstadoVersion;
import com.nexusbattles.ms_chatbot.chat.motor.model.TemaConocimiento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TemaConocimientoRepository extends JpaRepository<TemaConocimiento, UUID> {

    // HU-CHA-012: el motor responde solo con los temas activos de la version
    // en PRODUCCION. Antes de V4 leia "todos los activos"; con varias
    // versiones eso mezclaria la candidata y las retiradas con la vigente.
    List<TemaConocimiento> findByVersionEstadoAndActivoTrue(EstadoVersion estado);

    // HU-CHA-012: titulos de los temas frecuentes a partir de su clave estable.
    List<TemaConocimiento> findByClaveIn(Collection<String> claves);

    // HU-CHA-012 (CRUD): todos los temas de una version, activos o no.
    List<TemaConocimiento> findByVersionIdOrderByTituloAsc(UUID versionId);

    // HU-CHA-012 (CRUD): un tema SOLO si pertenece a esa version. Asi el panel
    // no puede editar por accidente un tema de produccion o de una retirada.
    Optional<TemaConocimiento> findByIdAndVersionId(UUID id, UUID versionId);

    // HU-CHA-012 (importacion): vaciar la candidata antes de cargar el archivo.
    void deleteByVersionId(UUID versionId);
}
