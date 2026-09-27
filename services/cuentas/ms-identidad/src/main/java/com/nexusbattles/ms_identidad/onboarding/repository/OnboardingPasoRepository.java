package com.nexusbattles.ms_identidad.onboarding.repository;

import com.nexusbattles.ms_identidad.onboarding.model.OnboardingPaso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OnboardingPasoRepository extends JpaRepository<OnboardingPaso, OnboardingPaso.Clave> {

    List<OnboardingPaso> findByUsuarioUid(UUID usuarioUid);
}
