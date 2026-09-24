package com.nexusbattles.ms_chatbot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// HU-CHA-012: activa las tareas programadas (@Scheduled). Hoy la unica es la
// evaluacion diaria de la base de conocimiento (VigilanciaBaseConocimientoTarea).
@Configuration
@EnableScheduling
public class ProgramacionConfig {
}
