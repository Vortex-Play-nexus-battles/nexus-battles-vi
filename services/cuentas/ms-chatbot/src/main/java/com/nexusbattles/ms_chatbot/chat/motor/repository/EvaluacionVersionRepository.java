package com.nexusbattles.ms_chatbot.chat.motor.repository;

import com.nexusbattles.ms_chatbot.chat.motor.model.EvaluacionVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EvaluacionVersionRepository extends JpaRepository<EvaluacionVersion, UUID> {

    // La evaluacion mas reciente de una version: la evaluacion periodica la
    // compara con la nueva para detectar desempeno degradado.
    Optional<EvaluacionVersion> findFirstByVersionIdOrderByFechaDesc(UUID versionId);
}
