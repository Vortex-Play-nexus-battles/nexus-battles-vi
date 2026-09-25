package com.nexusbattles.ms_chatbot.chat.identidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

// B11 (ms-chatbot.yaml 2.0.0): la sesion de un visitante, EMITIDA POR EL
// SERVIDOR. Se guarda solo la huella SHA-256 del identificador que se le
// entrego al navegador: quien lea la base no obtiene identificadores que
// sirvan para abrir la conversacion de nadie.
//
// Caduca por inactividad (expiraEn se renueva con cada mensaje): el documento
// pide historial «persistente durante la sesion del usuario» (7.4.2), no para
// siempre.
@Entity
@Table(name = "sesiones_anonimas")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // exigido por JPA
public class SesionAnonima {

    @Id
    private UUID id;

    @Column(nullable = false, length = 64, unique = true)
    private String huella;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn;

    @Column(name = "ultima_actividad", nullable = false)
    private Instant ultimaActividad;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    public SesionAnonima(String huella, Instant ahora, Duration inactividadMaxima) {
        this.id = UUID.randomUUID();
        this.huella = huella;
        this.creadaEn = ahora;
        this.ultimaActividad = ahora;
        this.expiraEn = ahora.plus(inactividadMaxima);
    }

    public boolean vigente(Instant ahora) {
        return expiraEn.isAfter(ahora);
    }

    public void renovar(Instant ahora, Duration inactividadMaxima) {
        this.ultimaActividad = ahora;
        this.expiraEn = ahora.plus(inactividadMaxima);
    }

    // La clave de la conversacion del visitante: nunca coincide con un uid (que
    // es un UUID a secas) ni con la clave de otro visitante.
    public String claveDeConversacion() {
        return IdentidadDelChat.PREFIJO_VISITANTE + id;
    }
}
