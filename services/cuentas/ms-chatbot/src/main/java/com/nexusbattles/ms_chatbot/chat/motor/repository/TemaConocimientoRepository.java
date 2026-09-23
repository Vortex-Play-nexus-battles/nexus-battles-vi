package com.nexusbattles.ms_chatbot.chat.motor.repository;

import com.nexusbattles.ms_chatbot.chat.motor.model.TemaConocimiento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TemaConocimientoRepository extends JpaRepository<TemaConocimiento, UUID> {

    List<TemaConocimiento> findByActivoTrue();

    // HU-CHA-012: titulos de los temas frecuentes a partir de su clave estable.
    List<TemaConocimiento> findByClaveIn(Collection<String> claves);
}
