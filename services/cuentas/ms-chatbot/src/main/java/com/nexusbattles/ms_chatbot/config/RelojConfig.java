package com.nexusbattles.ms_chatbot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

// B11: la hora de las sesiones de visitante y del limite de frecuencia sale de
// un reloj inyectable, para que las pruebas puedan moverlo sin esperar.
@Configuration
public class RelojConfig {

    @Bean
    public Clock relojDelChatbot() {
        return Clock.systemUTC();
    }
}
