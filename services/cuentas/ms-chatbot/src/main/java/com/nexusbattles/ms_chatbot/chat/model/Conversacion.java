package com.nexusbattles.ms_chatbot.chat.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

// HU-CHA-001: una Conversacion es el hilo persistente de chat entre un
// visitante o jugador y el chatbot. Se identifica por 'identificadorSesion'
// en vez de por usuarioId porque un visitante no autenticado tambien tiene
// derecho a una conversacion con historial (criterio de aceptacion: "Funciona
// para visitantes y para usuarios registrados").
//
// Para un visitante, identificadorSesion es un token anonimo generado por el
// frontend (p. ej. un UUID guardado en localStorage). Para un usuario
// autenticado, es su publicId (RellenoDeIdentificadorPublico, ms-identidad),
// asi la conversacion persiste entre dispositivos, como pide HU-CHA-008
// ("recuerda mis preferencias de conversaciones anteriores").
@Entity
@Table(name = "conversaciones")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // exigido por JPA
public class Conversacion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "identificador_sesion", nullable = false, unique = true)
    private String identificadorSesion;

    @Column(nullable = false)
    private boolean autenticado;

    @Column(name = "fecha_inicio", nullable = false)
    private Instant fechaInicio;

    @Column(name = "fecha_ultima_actividad", nullable = false)
    private Instant fechaUltimaActividad;

    public Conversacion(String identificadorSesion, boolean autenticado) {
        this.identificadorSesion = identificadorSesion;
        this.autenticado = autenticado;
        Instant ahora = Instant.now();
        this.fechaInicio = ahora;
        this.fechaUltimaActividad = ahora;
    }

    // Se llama cada vez que se agrega un mensaje nuevo, para que la
    // conversacion refleje cuando fue la ultima vez que se uso (util a
    // futuro para politicas de expiracion de historial).
    public void registrarActividad() {
        this.fechaUltimaActividad = Instant.now();
    }
}
