package com.nexusbattles.ms_chatbot.chat.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

// HU-CHA-011: calificacion util / no util de UNA respuesta del bot, con
// comentario opcional. Es OneToOne con Mensaje porque la regla de negocio
// es "no se admite calificar dos veces la misma respuesta"; el indice unico
// uk_calificaciones_mensaje_id (V3) lo garantiza en la base.
@Entity
@Table(name = "calificaciones")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // exigido por JPA
public class Calificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "mensaje_id", nullable = false, unique = true)
    private Mensaje mensaje;

    @Column(nullable = false)
    private boolean util;

    @Column(columnDefinition = "TEXT")
    private String comentario;

    @Column(name = "fecha_calificacion", nullable = false)
    private Instant fechaCalificacion;

    public Calificacion(Mensaje mensaje, boolean util, String comentario) {
        this.mensaje = mensaje;
        this.util = util;
        this.comentario = comentario;
        this.fechaCalificacion = Instant.now();
    }
}
