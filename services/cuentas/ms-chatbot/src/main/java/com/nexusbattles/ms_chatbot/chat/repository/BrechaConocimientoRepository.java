package com.nexusbattles.ms_chatbot.chat.repository;

import com.nexusbattles.ms_chatbot.chat.model.BrechaConocimiento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BrechaConocimientoRepository extends JpaRepository<BrechaConocimiento, UUID> {

    // Clave de agrupacion de HU-CHA-011: si ya existe una brecha para esta
    // pregunta normalizada, se incrementa su contador en vez de crear otra.
    Optional<BrechaConocimiento> findByTextoNormalizado(String textoNormalizado);
}
