package com.nexusbattles.ms_chatbot.chat.motor.repository;

import com.nexusbattles.ms_chatbot.chat.motor.model.CasoEvaluacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CasoEvaluacionRepository extends JpaRepository<CasoEvaluacion, UUID> {

    // Los casos con los que se evalua una version.
    List<CasoEvaluacion> findByActivoTrue();

    // Para el panel: todos, los mas nuevos primero.
    List<CasoEvaluacion> findAllByOrderByFechaCreacionDesc();
}
