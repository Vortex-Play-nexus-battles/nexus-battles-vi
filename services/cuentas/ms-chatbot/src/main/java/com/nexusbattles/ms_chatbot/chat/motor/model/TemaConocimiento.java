package com.nexusbattles.ms_chatbot.chat.motor.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Entrada de la base de conocimiento del chatbot (HU-CHA-004).
 * Cada fila representa un "tema" que el motor de respuestas puede
 * reconocer a partir de sus palabras clave (español/inglés) y para el
 * cual tiene una respuesta ya redactada en ambos idiomas.
 */
@Entity
@Table(name = "temas_conocimiento")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TemaConocimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Categoria categoria;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_respuesta", nullable = false, length = 40)
    private TipoRespuesta tipoRespuesta;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(name = "palabras_clave_es", nullable = false, columnDefinition = "TEXT")
    private String palabrasClaveEs;

    @Column(name = "palabras_clave_en", columnDefinition = "TEXT")
    private String palabrasClaveEn;

    @Column(name = "contenido_respuesta_es", nullable = false, columnDefinition = "TEXT")
    private String contenidoRespuestaEs;

    @Column(name = "contenido_respuesta_en", columnDefinition = "TEXT")
    private String contenidoRespuestaEn;

    @Column(nullable = false)
    private boolean activo = true;

    public TemaConocimiento(Categoria categoria, TipoRespuesta tipoRespuesta, String titulo,
                            String palabrasClaveEs, String palabrasClaveEn,
                            String contenidoRespuestaEs, String contenidoRespuestaEn) {
        this.categoria = categoria;
        this.tipoRespuesta = tipoRespuesta;
        this.titulo = titulo;
        this.palabrasClaveEs = palabrasClaveEs;
        this.palabrasClaveEn = palabrasClaveEn;
        this.contenidoRespuestaEs = contenidoRespuestaEs;
        this.contenidoRespuestaEn = contenidoRespuestaEn;
    }
}
