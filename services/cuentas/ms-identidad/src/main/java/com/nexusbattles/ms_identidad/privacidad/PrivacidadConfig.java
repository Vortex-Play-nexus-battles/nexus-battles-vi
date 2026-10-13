package com.nexusbattles.ms_identidad.privacidad;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita la tarea programada del derecho al olvido ({@link EjecutorDeCierres})
 * por si misma, sin depender de que siga existiendo la del alta del jugador
 * ({@code OnboardingConfig}). Declararlo dos veces no registra dos
 * planificadores.
 */
@Configuration
@EnableScheduling
public class PrivacidadConfig {
}
