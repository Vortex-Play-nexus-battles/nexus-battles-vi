package com.nexusbattles.ms_identidad.onboarding;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita el reintento programado del alta ({@code ReintentosDeAlta}). Es la
 * unica tarea programada de ms-identidad; se apaga con
 * {@code app.onboarding.reintentos-automaticos=false}.
 */
@Configuration
@EnableScheduling
public class OnboardingConfig {
}
