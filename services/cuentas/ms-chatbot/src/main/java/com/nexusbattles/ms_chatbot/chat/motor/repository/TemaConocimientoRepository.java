package com.nexusbattles.ms_chatbot.chat.motor.repository;

import com.nexusbattles.ms_chatbot.chat.motor.model.TemaConocimiento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TemaConocimientoRepository extends JpaRepository<TemaConocimiento, UUID> {

    List<TemaConocimiento> findByActivoTrue();
}
